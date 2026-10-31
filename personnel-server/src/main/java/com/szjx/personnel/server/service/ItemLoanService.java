package com.szjx.personnel.server.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.szjx.personnel.server.dao.ItemLoanDao;
import com.szjx.personnel.server.model.ItemLoan;
import com.szjx.personnel.server.model.PageResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

@Service
public class ItemLoanService {
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999.99");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);
    private final ItemLoanDao dao;

    public ItemLoanService(ItemLoanDao dao) { this.dao = dao; }

    public PageResult<ItemLoan> query(String itemName, String borrowerName,
                                      String registrationDepartment, String status, int page, int size) {
        registrationDepartment = text(registrationDepartment, "登记单位", 200, false);
        itemName = text(itemName, "物品名称", 100, false);
        borrowerName = text(borrowerName, "借用人姓名", 100, false);
        status = text(status, "状态", 20, false);
        if (!status.isEmpty() && !"borrowing".equals(status) && !"returned".equals(status)) {
            throw new IllegalArgumentException("状态仅支持 borrowing、returned");
        }
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 50;
        LocalDate today = LocalDate.now();
        return new PageResult<ItemLoan>(dao.count(itemName, borrowerName, registrationDepartment, status, today),
                dao.query(itemName, borrowerName, registrationDepartment, status, page, size, today));
    }

    public ItemLoan create(ItemLoan loan) {
        validateLoan(loan);
        resolveHandler(loan, null);
        LocalDate today = LocalDate.now();
        return dao.findById(dao.insert(loan), today);
    }

    public ItemLoan update(Long id, ItemLoan loan) {
        LocalDate today = LocalDate.now();
        ItemLoan original = required(id, today);
        requireUnreturned(original);
        validateLoan(loan);
        resolveHandler(loan, original);
        if (dao.update(id, original.getOwnerUserId(), loan) != 1) throw changed();
        return dao.findById(id, today);
    }

    public ItemLoan returnLoan(Long id, ReturnRequest request) {
        LocalDate today = LocalDate.now();
        ItemLoan original = required(id, today);
        requireUnreturned(original);
        if (request == null) throw new IllegalArgumentException("归还信息不能为空");
        String condition = text(request.returnCondition, "归还情况", 500, true);
        String remark = text(request.returnRemark, "归还备注", 1000, false);
        if (dao.returnLoan(original, condition, remark) != 1) throw changed();
        return dao.findById(id, today);
    }

    public void delete(Long id) {
        ItemLoan original = required(id, LocalDate.now());
        if (dao.delete(id, original.getOwnerUserId()) != 1) throw changed();
    }

    private ItemLoan required(Long id, LocalDate today) {
        if (id == null || id <= 0) throw new IllegalArgumentException("记录ID必须为正数");
        ItemLoan original = dao.findById(id, today);
        if (original == null) throw new IllegalArgumentException("物品借还记录不存在");
        return original;
    }

    private void validateLoan(ItemLoan loan) {
        if (loan == null) throw new IllegalArgumentException("借出信息不能为空");
        if (loan.getPersonnelId() != null && loan.getPersonnelId() != 0L) {
            throw new IllegalArgumentException("借用人已改为独立登记，请填写借用人信息，不能关联人员ID");
        }
        loan.setPersonnelId(0L);
        loan.setRegistrationDepartment(text(loan.getRegistrationDepartment(), "登记单位", 200, true));
        loan.setBorrowerName(text(loan.getBorrowerName(), "借用人姓名", 100, true));
        loan.setBorrowerIdCard(text(loan.getBorrowerIdCard(), "借用人身份证号", 50, false));
        loan.setBorrowerPhone(text(loan.getBorrowerPhone(), "借用人联系方式", 50, false));
        loan.setBorrowerUnit(text(loan.getBorrowerUnit(), "借用人单位", 200, false));
        // Return state has one dedicated atomic transition, never a general create/update operation.
        if (hasValue(loan.getReturnCondition()) || hasValue(loan.getReturnRemark()) || hasValue(loan.getStatus())) {
            throw new IllegalArgumentException("创建或编辑不能设置归还信息及状态，请使用归还接口");
        }
        loan.setItemName(text(loan.getItemName(), "物品名称", 100, true));
        loan.setSpecification(text(loan.getSpecification(), "规格型号", 200, false));
        loan.setUnit(text(loan.getUnit(), "物品单位", 20, true));
        loan.setRemark(text(loan.getRemark(), "备注", 1000, false));
        BigDecimal quantity = loan.getQuantity();
        if (quantity == null || quantity.signum() <= 0 || quantity.compareTo(MAX_QUANTITY) > 0 || quantity.scale() > 2) {
            throw new IllegalArgumentException("数量必须大于0、最多2位小数且不超过999999999.99");
        }
        // Bounds above precede normalization, preventing huge exponents from allocating huge strings.
        loan.setQuantity(quantity.setScale(2));
        LocalDate borrow = date(loan.getBorrowDate(), "借出日期", true);
        loan.setBorrowDate(borrow.toString());
    }

    private void resolveHandler(ItemLoan loan, ItemLoan original) {
        Long id = loan.getHandlerId();
        if (original != null && (id == null || id.equals(original.getHandlerId()))) {
            // Historical snapshots remain valid after personnel rename, deletion or scope changes.
            loan.setHandlerId(original.getHandlerId());
            loan.setHandlerName(original.getHandlerName());
            loan.setHandlerIdCard(original.getHandlerIdCard());
            return;
        }
        if (id == null || id == 0L) {
            loan.setHandlerId(0L);
            loan.setHandlerName("");
            loan.setHandlerIdCard("");
            return;
        }
        if (id < 0L) throw new IllegalArgumentException("经手人人员ID必须为正数");
        java.util.Map<String, String> snapshot = dao.findHandlerSnapshot(id);
        loan.setHandlerName(text(snapshot.get("name"), "经手人姓名", 100, true));
        loan.setHandlerIdCard(text(snapshot.get("idCard"), "经手人身份证号", 50, false));
    }

    private boolean hasValue(String text) { return text != null && !text.isEmpty(); }

    private String text(String value, String label, int max, boolean required) {
        if (value == null) value = "";
        value = value.trim();
        if (value.length() > max) throw new IllegalArgumentException(label + "不能超过" + max + "个字符");
        if (required && value.isEmpty()) throw new IllegalArgumentException(label + "不能为空");
        return value;
    }

    private LocalDate date(String value, String label, boolean required) {
        if (value == null || value.isEmpty()) {
            if (required) throw new IllegalArgumentException(label + "不能为空");
            return null;
        }
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || value.startsWith("0000")) {
            throw new IllegalArgumentException(label + "必须为真实完整日期 yyyy-MM-dd");
        }
        try { return LocalDate.parse(value, DATE); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException(label + "必须为真实完整日期 yyyy-MM-dd"); }
    }

    private void requireUnreturned(ItemLoan loan) {
        if ("returned".equals(loan.getStatus())) throw changed();
    }

    private DataIntegrityViolationException changed() {
        return new DataIntegrityViolationException("记录已归还或已变更，请刷新后重试");
    }

    public static class ReturnRequest {
        @JsonDeserialize(using = RequiredTextDeserializer.class)
        public String returnCondition;
        public String returnRemark;
    }

    /** Do not coerce JSON numbers/booleans to a return-condition description. */
    public static class RequiredTextDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws java.io.IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return (String) context.handleUnexpectedToken(String.class, parser);
            }
            return parser.getText();
        }
    }
}
