package com.szjx.personnel.server.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

    /** 独立物品借还记录；数据归属由服务端按创建者账号确定。 */
public class ItemLoan {
    private Long id;
    private String itemName;
    private String specification;
    private BigDecimal quantity;
    private String unit;
    @JsonDeserialize(using = BorrowerTextDeserializer.class)
    private String borrowerName;
    @JsonDeserialize(using = BorrowerTextDeserializer.class)
    private String borrowerIdCard;
    @JsonDeserialize(using = BorrowerTextDeserializer.class)
    private String borrowerPhone;
    @JsonDeserialize(using = BorrowerTextDeserializer.class)
    private String borrowerUnit;
    @JsonDeserialize(using = BorrowerTextDeserializer.class)
    private String registrationDepartment;
    // Legacy response compatibility only; never establishes a personnel association.
    private Long personnelId;
    private String personnelName;
    private Long handlerId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String handlerName;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String handlerIdCard;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long ownerUserId;
    private String borrowDate;
    private String returnCondition;
    private String returnRemark;
    private String remark;
    private String status;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getBorrowerName() { return borrowerName; }
    public void setBorrowerName(String borrowerName) { this.borrowerName = borrowerName; }
    public String getBorrowerIdCard() { return borrowerIdCard; }
    public void setBorrowerIdCard(String borrowerIdCard) { this.borrowerIdCard = borrowerIdCard; }
    public String getBorrowerPhone() { return borrowerPhone; }
    public void setBorrowerPhone(String borrowerPhone) { this.borrowerPhone = borrowerPhone; }
    public String getBorrowerUnit() { return borrowerUnit; }
    public void setBorrowerUnit(String borrowerUnit) { this.borrowerUnit = borrowerUnit; }
    public String getRegistrationDepartment() { return registrationDepartment; }
    public void setRegistrationDepartment(String registrationDepartment) { this.registrationDepartment = registrationDepartment; }
    public Long getPersonnelId() { return personnelId; }
    public void setPersonnelId(Long personnelId) { this.personnelId = personnelId; }
    public String getPersonnelName() { return personnelName; }
    public void setPersonnelName(String personnelName) { this.personnelName = personnelName; }
    public Long getHandlerId() { return handlerId; }
    public void setHandlerId(Long handlerId) { this.handlerId = handlerId; }
    public String getHandlerName() { return handlerName; }
    public void setHandlerName(String handlerName) { this.handlerName = handlerName; }
    public String getHandlerIdCard() { return handlerIdCard; }
    public void setHandlerIdCard(String handlerIdCard) { this.handlerIdCard = handlerIdCard; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getBorrowDate() { return borrowDate; }
    public void setBorrowDate(String borrowDate) { this.borrowDate = borrowDate; }
    public String getReturnCondition() { return returnCondition; }
    public void setReturnCondition(String returnCondition) { this.returnCondition = returnCondition; }
    public String getReturnRemark() { return returnRemark; }
    public void setReturnRemark(String returnRemark) { this.returnRemark = returnRemark; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    /** Identity/contact values must arrive as text, never coerced numbers that lose leading zeroes. */
    public static class BorrowerTextDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws java.io.IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return (String) context.handleUnexpectedToken(String.class, parser);
            }
            return parser.getText();
        }
    }
}
