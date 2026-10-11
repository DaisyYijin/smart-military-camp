package com.szjx.personnel.client.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.szjx.personnel.client.model.DepartmentNode;
import com.szjx.personnel.client.model.PersonnelItem;
import com.szjx.personnel.client.net.ApiClient;
import com.szjx.personnel.client.net.ApiException;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 人员新增/编辑对话框(分组表单,含详细个人信息)
 */
public class PersonnelDialog extends JDialog {

    private static final String[] GENDERS = {"男", "女"};

    private final ApiClient api;
    private final boolean editMode;
    private final Runnable onSaved;

    // 基本信息
    private final JTextField nameField = new JTextField(14);
    private final JComboBox<String> genderBox = new JComboBox<String>(GENDERS);
    private final JTextField idCardField = new JTextField(14);
    private final JTextField birthdayField = new JTextField(10);
    private final JTextField phoneField = new JTextField(12);
    private final JTextField addressField = new JTextField(14);

    // 部队信息
    private final JComboBox<Object> deptBox = new JComboBox<Object>();
    private final JComboBox<String> rankBox = new JComboBox<String>();
    private final JTextField postField = new JTextField(12);
    private final JComboBox<String> statusBox = new JComboBox<String>();
    private final JTextField entryDateField = new JTextField(10);

    // 详细信息
    private final JComboBox<String> bloodBox = new JComboBox<String>();
    private final JComboBox<String> nationBox = new JComboBox<String>();
    private final JComboBox<String> politicalBox = new JComboBox<String>();
    private final JComboBox<String> maritalBox = new JComboBox<String>();
    private final JComboBox<String> eduBox = new JComboBox<String>();
    private final JComboBox<String> onlyChildBox = new JComboBox<String>();
    private final JTextField partyJoinField = new JTextField(10);
    private final JTextField nativePlaceField = new JTextField(12);
    private final JTextField spouseNameField = new JTextField(12);
    private final JTextField spouseWorkField = new JTextField(14);
    private final JTextField spousePhoneField = new JTextField(12);
    private final JTextField spouseIdCardField = new JTextField(16);
    private final JTextField spouseJobField = new JTextField(12);
    private final JTextField marriageDateField = new JTextField(10);
    private final JPanel spousePanel = new JPanel();
    private final JTextField heightField = new JTextField(6);
    private final JTextField weightField = new JTextField(6);

    // 其他
    private final JTextArea remarkArea = new JTextArea(3, 18);
    private final JLabel photoLabel = new JLabel("未选择照片", JLabel.CENTER);
    private String photoBase64;
    private boolean photoRemoved = false;

    private PersonnelItem editing;
    private final List<Object[]> deptOptions = new ArrayList<Object[]>();

    private PersonnelDialog(java.awt.Window parent, ApiClient api, boolean editMode,
                            PersonnelItem editing, List<DepartmentNode> deptTree,
                            Runnable onSaved) {
        super(parent, editMode ? "编辑人员" : "新增人员", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.editMode = editMode;
        this.editing = editing;
        this.onSaved = onSaved;

        rankBox.setEditable(true);
        statusBox.setEditable(true);
        nationBox.setEditable(true);
        bloodBox.setEditable(true);
        politicalBox.setEditable(true);
        maritalBox.setEditable(true);
        eduBox.setEditable(true);
        onlyChildBox.setEditable(true);

        // 下拉框统一限宽,避免被最长选项文字撑宽导致出现水平滚动条
        for (JComboBox<?> box : new JComboBox<?>[]{
                genderBox, deptBox, rankBox, statusBox, bloodBox, nationBox,
                politicalBox, maritalBox, eduBox, onlyChildBox}) {
            box.setPreferredSize(new Dimension(120, 28));
        }

        // 军衔与状态从字典加载
        com.szjx.personnel.client.util.DictLoader.load(api, "rank", rankBox, null, new Runnable() {
            @Override
            public void run() {
                if (editMode && editing != null) {
                    rankBox.setSelectedItem(hasText(editing.getRank()) ? editing.getRank() : "无");
                }
            }
        });
        com.szjx.personnel.client.util.DictLoader.load(api, "personnel_status", statusBox, null, new Runnable() {
            @Override
            public void run() {
                if (editMode && editing != null) {
                    statusBox.setSelectedItem(hasText(editing.getStatus()) ? editing.getStatus() : "在职");
                }
            }
        });
        // 详细信息下拉从字典加载
        loadDetailDict("blood_type", bloodBox, "未知", new java.util.function.Function<PersonnelItem, String>() {
            @Override
            public String apply(PersonnelItem p) {
                return p.getBloodType();
            }
        });
        loadDetailDict("nation", nationBox, null, new java.util.function.Function<PersonnelItem, String>() {
            @Override
            public String apply(PersonnelItem p) {
                return p.getNation();
            }
        });
        loadDetailDict("political_status", politicalBox, "群众", new java.util.function.Function<PersonnelItem, String>() {
            @Override
            public String apply(PersonnelItem p) {
                return p.getPoliticalStatus();
            }
        });
        loadDetailDict("marital_status", maritalBox, "未婚", new java.util.function.Function<PersonnelItem, String>() {
            @Override
            public String apply(PersonnelItem p) {
                return p.getMaritalStatus();
            }
        });
        loadDetailDict("education", eduBox, "其他", new java.util.function.Function<PersonnelItem, String>() {
            @Override
            public String apply(PersonnelItem p) {
                return p.getEducation();
            }
        });
        loadDetailDict("only_child", onlyChildBox, "否", new java.util.function.Function<PersonnelItem, String>() {
            @Override
            public String apply(PersonnelItem p) {
                return p.getOnlyChild();
            }
        });

        initForm(deptTree);
        if (editMode && editing != null) {
            fillForm(editing);
        }
        setSize(680, 720);
        setMinimumSize(new Dimension(640, 620));
        setLocationRelativeTo(parent);
    }

    public static void showAdd(java.awt.Component parent, ApiClient api,
                               List<DepartmentNode> deptTree, Runnable onSaved) {
        new PersonnelDialog(SwingUtilities.getWindowAncestor(parent), api, false, null, deptTree, onSaved)
                .setVisible(true);
    }

    public static void showEdit(java.awt.Component parent, ApiClient api, PersonnelItem item,
                                List<DepartmentNode> deptTree, Runnable onSaved) {
        new PersonnelDialog(SwingUtilities.getWindowAncestor(parent), api, true, item, deptTree, onSaved)
                .setVisible(true);
    }

    private void initForm(List<DepartmentNode> deptTree) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int y = 0;
        // ===== 基本信息 =====
        c.gridx = 0;
        c.gridy = y;
        c.gridwidth = 2;
        form.add(sectionLabel("基本信息"), c);
        c.gridwidth = 1;
        y++;

        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("姓名*:"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(nameField, c);
        c.gridx = 2;
        c.weightx = 0;
        form.add(new JLabel("性别:"), c);
        c.gridx = 3;
        form.add(genderBox, c);
        y++;

        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("身份证号:"), c);
        c.gridx = 1;
        form.add(idCardField, c);
        c.gridx = 2;
        form.add(new JLabel("出生日期:"), c);
        c.gridx = 3;
        birthdayField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "yyyy-MM-dd");
        form.add(birthdayField, c);
        y++;

        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("手机号:"), c);
        c.gridx = 1;
        form.add(phoneField, c);
        c.gridx = 2;
        form.add(new JLabel("家庭住址:"), c);
        c.gridx = 3;
        form.add(addressField, c);
        y++;

        // ===== 部队信息 =====
        c.gridx = 0;
        c.gridy = y;
        c.gridwidth = 2;
        form.add(sectionLabel("部队信息"), c);
        c.gridwidth = 1;
        y++;

        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("部门:"), c);
        c.gridx = 1;
        fillDeptBox(deptTree);
        form.add(deptBox, c);
        c.gridx = 2;
        form.add(new JLabel("军衔:"), c);
        c.gridx = 3;
        form.add(rankBox, c);
        y++;

        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("职务:"), c);
        c.gridx = 1;
        form.add(postField, c);
        c.gridx = 2;
        form.add(new JLabel("状态:"), c);
        c.gridx = 3;
        form.add(statusBox, c);
        y++;

        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("入伍日期:"), c);
        c.gridx = 1;
        entryDateField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "yyyy-MM-dd");
        form.add(entryDateField, c);
        y++;

        // ===== 详细信息 =====
        c.gridx = 0;
        c.gridy = y;
        c.gridwidth = 2;
        form.add(sectionLabel("详细信息"), c);
        c.gridwidth = 1;
        y++;

        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("血型:"), c);
        c.gridx = 1;
        form.add(bloodBox, c);
        c.gridx = 2;
        form.add(new JLabel("民族:"), c);
        c.gridx = 3;
        form.add(nationBox, c);
        y++;

        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("政治面貌:"), c);
        c.gridx = 1;
        form.add(politicalBox, c);
        c.gridx = 2;
        form.add(new JLabel("婚姻状况:"), c);
        c.gridx = 3;
        form.add(maritalBox, c);
        y++;

        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("学历:"), c);
        c.gridx = 1;
        form.add(eduBox, c);
        c.gridx = 2;
        form.add(new JLabel("是否独生子女:"), c);
        c.gridx = 3;
        form.add(onlyChildBox, c);
        y++;

        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("籍贯:"), c);
        c.gridx = 1;
        form.add(nativePlaceField, c);
        c.gridx = 2;
        form.add(new JLabel("身高(cm):"), c);
        c.gridx = 3;
        form.add(heightField, c);
        y++;

        c.gridx = 2;
        c.gridy = y;
        form.add(new JLabel("体重(kg):"), c);
        c.gridx = 3;
        form.add(weightField, c);
        y++;

        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("党团时间:"), c);
        c.gridx = 1;
        partyJoinField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "入党/入团日期 yyyy-MM-dd");
        form.add(partyJoinField, c);
        y++;

        // ===== 配偶信息(婚姻状况为已婚时显示) =====
        c.gridx = 0;
        c.gridy = y;
        c.gridwidth = 4;
        spousePanel.setBorder(new TitledBorder("配偶信息(婚姻状况为已婚时填写)"));
        spousePanel.setLayout(new GridBagLayout());
        GridBagConstraints sc = new GridBagConstraints();
        sc.insets = new Insets(4, 6, 4, 6);
        sc.anchor = GridBagConstraints.WEST;
        sc.fill = GridBagConstraints.HORIZONTAL;
        sc.gridx = 0;
        sc.gridy = 0;
        spousePanel.add(new JLabel("配偶姓名:"), sc);
        sc.gridx = 1;
        sc.weightx = 1;
        spousePanel.add(spouseNameField, sc);
        sc.gridx = 2;
        sc.weightx = 0;
        spousePanel.add(new JLabel("结婚日期:"), sc);
        sc.gridx = 3;
        marriageDateField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "yyyy-MM-dd");
        spousePanel.add(marriageDateField, sc);
        sc.gridx = 0;
        sc.gridy = 1;
        spousePanel.add(new JLabel("配偶身份证号:"), sc);
        sc.gridx = 1;
        spousePanel.add(spouseIdCardField, sc);
        sc.gridx = 2;
        spousePanel.add(new JLabel("工作单位:"), sc);
        sc.gridx = 3;
        spousePanel.add(spouseWorkField, sc);
        sc.gridx = 0;
        sc.gridy = 2;
        spousePanel.add(new JLabel("联系电话:"), sc);
        sc.gridx = 1;
        spousePanel.add(spousePhoneField, sc);
        sc.gridx = 2;
        spousePanel.add(new JLabel("职业:"), sc);
        sc.gridx = 3;
        spousePanel.add(spouseJobField, sc);
        spousePanel.setVisible(false);
        form.add(spousePanel, c);
        c.gridwidth = 1;
        y++;

        // 婚姻状况切换时控制配偶区显示
        maritalBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean married = "已婚".equals(maritalBox.getSelectedItem());
                spousePanel.setVisible(married);
                revalidate();
                repaint();
            }
        });

        // ===== 备注 =====
        c.gridx = 0;
        c.gridy = y;
        c.gridwidth = 2;
        form.add(sectionLabel("备注"), c);
        c.gridwidth = 1;
        y++;
        c.gridx = 0;
        c.gridy = y;
        c.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("备注:"), c);
        c.gridx = 1;
        c.gridwidth = 3;
        remarkArea.setLineWrap(true);
        JScrollPane remarkScroll = new JScrollPane(remarkArea);
        remarkScroll.setPreferredSize(new Dimension(380, 60));
        form.add(remarkScroll, c);
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.WEST;
        y++;

        // ===== 照片 =====
        c.gridx = 0;
        c.gridy = y;
        c.gridwidth = 4;
        JPanel photoPanel = new JPanel(new BorderLayout(4, 4));
        photoPanel.setBorder(new TitledBorder("照片(可选,jpg/png ≤5MB)"));
        photoLabel.setPreferredSize(new Dimension(140, 170));
        photoLabel.setBorder(BorderFactory.createLineBorder(java.awt.Color.GRAY));
        JPanel photoBtns = new JPanel();
        JButton chooseBtn = new JButton("选择照片");
        JButton removeBtn = new JButton("移除");
        chooseBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                choosePhoto();
            }
        });
        removeBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                photoBase64 = null;
                photoRemoved = true;
                photoLabel.setIcon(null);
                photoLabel.setText("未选择照片");
            }
        });
        photoBtns.add(chooseBtn);
        photoBtns.add(removeBtn);
        photoPanel.add(photoLabel, BorderLayout.CENTER);
        photoPanel.add(photoBtns, BorderLayout.SOUTH);
        form.add(photoPanel, c);
        c.gridwidth = 1;

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        JButton saveBtn = new JButton("保 存");
        JButton cancelBtn = new JButton("取 消");
        saveBtn.setPreferredSize(new Dimension(100, 32));
        saveBtn.putClientProperty(FlatClientProperties.STYLE,
                "arc: 10; background: #1D63ED; foreground: #FFFFFF; focusWidth: 0; innerFocusWidth: 0;");
        saveBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doSave();
            }
        });
        cancelBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        bottom.add(saveBtn);
        bottom.add(cancelBtn);
        add(bottom, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(saveBtn);
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 13));
        label.setForeground(new java.awt.Color(0x1D63ED));
        return label;
    }

    private void fillDeptBox(List<DepartmentNode> deptTree) {
        deptBox.removeAllItems();
        deptOptions.clear();
        deptBox.addItem(0L);
        for (DepartmentNode n : deptTree) {
            addDeptOptions(n, 0);
        }
        deptBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                                                                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Long) {
                    Long id = (Long) value;
                    label.setText(id == 0L ? "未分配" : deptName(id));
                }
                return label;
            }
        });
    }

    private void addDeptOptions(DepartmentNode n, int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("　 ");
        }
        sb.append(n.getName());
        deptOptions.add(new Object[]{n.getId(), sb.toString()});
        deptBox.addItem(n.getId());
        if (n.getChildren() != null) {
            for (DepartmentNode child : n.getChildren()) {
                addDeptOptions(child, depth + 1);
            }
        }
    }

    private String deptName(Long id) {
        for (Object[] o : deptOptions) {
            if (o[0].equals(id)) {
                return (String) o[1];
            }
        }
        return "";
    }

    private void loadDetailDict(String type, final JComboBox<String> box, final String defaultVal,
                                final java.util.function.Function<PersonnelItem, String> valueGetter) {
        com.szjx.personnel.client.util.DictLoader.load(api, type, box, null, new Runnable() {
            @Override
            public void run() {
                if (editMode && editing != null) {
                    String v = valueGetter.apply(editing);
                    box.setSelectedItem(hasText(v) ? v : defaultVal);
                } else if (defaultVal != null) {
                    box.setSelectedItem(defaultVal);
                }
            }
        });
    }

    private void fillForm(PersonnelItem p) {
        nameField.setText(nvl(p.getName()));
        genderBox.setSelectedItem(nvl(p.getGender()));
        idCardField.setText(nvl(p.getIdCard()));
        birthdayField.setText(nvl(p.getBirthday()));
        phoneField.setText(nvl(p.getPhone()));
        addressField.setText(nvl(p.getAddress()));
        selectDept(p.getDepartmentId());
        postField.setText(nvl(p.getPost()));
        entryDateField.setText(nvl(p.getEntryDate()));
        // 详细信息(下拉由字典加载后回填)
        nativePlaceField.setText(nvl(p.getNativePlace()));
        partyJoinField.setText(nvl(p.getPartyJoinDate()));
        boolean married = "已婚".equals(p.getMaritalStatus());
        spousePanel.setVisible(married);
        spouseNameField.setText(nvl(p.getSpouseName()));
        spouseWorkField.setText(nvl(p.getSpouseWork()));
        spousePhoneField.setText(nvl(p.getSpousePhone()));
        spouseIdCardField.setText(nvl(p.getSpouseIdCard()));
        spouseJobField.setText(nvl(p.getSpouseJob()));
        marriageDateField.setText(nvl(p.getMarriageDate()));
        heightField.setText(p.getHeight() == null ? "" : String.valueOf(p.getHeight()));
        weightField.setText(p.getWeight() == null ? "" : String.valueOf(p.getWeight()));
        remarkArea.setText(nvl(p.getRemark()));
        if (hasText(p.getPhoto())) {
            loadRemotePhoto(p.getId());
        }
    }

    private void selectDept(Long id) {
        if (id == null) {
            deptBox.setSelectedItem(0L);
            return;
        }
        for (int i = 0; i < deptBox.getItemCount(); i++) {
            if (deptBox.getItemAt(i) instanceof Long && id.equals(deptBox.getItemAt(i))) {
                deptBox.setSelectedIndex(i);
                return;
            }
        }
        deptBox.setSelectedItem(0L);
    }

    private void loadRemotePhoto(final long id) {
        photoLabel.setText("加载中...");
        new SwingWorker<byte[], Void>() {
            @Override
            protected byte[] doInBackground() throws Exception {
                return api.download("/api/personnel/" + id + "/photo", null);
            }

            @Override
            protected void done() {
                try {
                    showPhoto(get());
                } catch (Exception e) {
                    photoLabel.setText("照片加载失败");
                }
            }
        }.execute();
    }

    private void choosePhoto() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择照片");
        chooser.setFileFilter(new FileNameExtensionFilter("图片文件(jpg/png)", "jpg", "jpeg", "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final File file = chooser.getSelectedFile();
        if (file.length() > 5 * 1024 * 1024) {
            JOptionPane.showMessageDialog(this, "照片不能超过 5MB", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        photoLabel.setText("读取中...");
        new SwingWorker<byte[], Void>() {
            @Override
            protected byte[] doInBackground() throws Exception {
                return java.nio.file.Files.readAllBytes(file.toPath());
            }

            @Override
            protected void done() {
                try {
                    byte[] bytes = get();
                    photoBase64 = Base64.getEncoder().encodeToString(bytes);
                    photoRemoved = false;
                    showPhoto(bytes);
                } catch (Exception ex) {
                    photoLabel.setText("未选择照片");
                    JOptionPane.showMessageDialog(PersonnelDialog.this,
                            "读取照片失败:" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showPhoto(byte[] bytes) {
        ImageIcon icon = new ImageIcon(bytes);
        Image img = icon.getImage().getScaledInstance(140, 170, Image.SCALE_SMOOTH);
        photoLabel.setIcon(new ImageIcon(img));
        photoLabel.setText("");
    }

    private void doSave() {
        final PersonnelItem p = new PersonnelItem();
        p.setName(nameField.getText().trim());
        p.setGender(String.valueOf(genderBox.getSelectedItem()));
        p.setIdCard(idCardField.getText().trim());
        p.setBirthday(birthdayField.getText().trim());
        p.setPhone(phoneField.getText().trim());
        p.setAddress(addressField.getText().trim());
        Object dept = deptBox.getSelectedItem();
        p.setDepartmentId(dept instanceof Long ? (Long) dept : 0L);
        p.setRank(String.valueOf(rankBox.getSelectedItem()));
        p.setPost(postField.getText().trim());
        p.setStatus(String.valueOf(statusBox.getSelectedItem()));
        p.setEntryDate(entryDateField.getText().trim());
        // 详细信息
        p.setBloodType(String.valueOf(bloodBox.getSelectedItem()));
        p.setNation(nationBox.getSelectedItem() == null ? "" : String.valueOf(nationBox.getSelectedItem()).trim());
        p.setPoliticalStatus(String.valueOf(politicalBox.getSelectedItem()));
        p.setMaritalStatus(String.valueOf(maritalBox.getSelectedItem()));
        p.setEducation(String.valueOf(eduBox.getSelectedItem()));
        p.setOnlyChild(String.valueOf(onlyChildBox.getSelectedItem()));
        p.setNativePlace(nativePlaceField.getText().trim());
        p.setPartyJoinDate(partyJoinField.getText().trim());
        p.setSpouseName(spouseNameField.getText().trim());
        p.setSpouseWork(spouseWorkField.getText().trim());
        p.setSpousePhone(spousePhoneField.getText().trim());
        p.setSpouseIdCard(spouseIdCardField.getText().trim());
        p.setSpouseJob(spouseJobField.getText().trim());
        p.setMarriageDate(marriageDateField.getText().trim());
        p.setHeight(parseDouble(heightField, "身高"));
        p.setWeight(parseDouble(weightField, "体重"));
        p.setRemark(remarkArea.getText().trim());

        if (p.getName().isEmpty()) {
            JOptionPane.showMessageDialog(this, "姓名不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (hasText(p.getIdCard()) && !p.getIdCard().matches("\\d{17}[\\dXx]|\\d{15}")) {
            JOptionPane.showMessageDialog(this, "身份证号格式不正确", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (photoRemoved) {
            p.setPhoto(""); // 明确删除
        }
        if (photoBase64 != null) {
            p.setPhotoBase64(photoBase64);
        }
        if (editMode) {
            p.setId(editing.getId());
        }

        setEnabled(false);
        final Gson gson = new Gson();
        new SwingWorker<JsonObject, Void>() {
            @Override
            protected JsonObject doInBackground() throws Exception {
                if (editMode) {
                    return api.put("/api/personnel/" + editing.getId(), p);
                }
                return api.post("/api/personnel", p);
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(PersonnelDialog.this, "保存成功", "提示", JOptionPane.INFORMATION_MESSAGE);
                    if (onSaved != null) {
                        onSaved.run();
                    }
                    dispose();
                } catch (Exception e) {
                    setEnabled(true);
                    String msg = e instanceof ApiException ? e.getMessage() : "保存失败:" + e.getMessage();
                    JOptionPane.showMessageDialog(PersonnelDialog.this, msg, "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private Double parseDouble(JTextField field, String label) {
        String s = field.getText().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, label + "必须是数字", "提示", JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
