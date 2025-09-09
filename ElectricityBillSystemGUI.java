import java.awt.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class ElectricityBillSystemGUI extends JFrame {

    private static final String CUSTOMERS_FILE = "customers.csv";
    private static final String BILLS_FILE = "bills.csv";

    private static final Map<Integer, Customer> customers = new HashMap<>();
    private static final java.util.List<Bill> bills = new ArrayList<>();
    private static int nextCustomerId = 1;
    private static int nextBillId = 1;

    private static final int[] SLAB_LIMITS = {50, 100, 100};
    private static final BigDecimal[] SLAB_RATES = {
            new BigDecimal("3.00"),
            new BigDecimal("5.50"),
            new BigDecimal("7.00")
    };
    private static final BigDecimal RATE_ABOVE = new BigDecimal("9.00");
    private static final BigDecimal FIXED_CHARGE = new BigDecimal("50.00");
    private static final BigDecimal METER_RENT = new BigDecimal("20.00");
    private static final BigDecimal TAX_RATE = new BigDecimal("0.05");

    private final DefaultTableModel customerModel;
    private final DefaultTableModel billModel;

    // Shared reference to combo
    private final JComboBox<Customer> custCombo = new JComboBox<>();

    public ElectricityBillSystemGUI() {
        super("Electricity Bill System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(850, 600);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();

        // ---------------- Customers Tab ----------------
        JPanel customerPanel = new JPanel(new BorderLayout());
        customerModel = new DefaultTableModel(new Object[]{"ID", "Name", "Address", "Meter No", "Phone"}, 0);
        JTable customerTable = new JTable(customerModel);
        customerPanel.add(new JScrollPane(customerTable), BorderLayout.CENTER);

        JPanel addCustomerPanel = new JPanel(new GridLayout(6, 2, 5, 5));
        JTextField nameField = new JTextField();
        JTextField addrField = new JTextField();
        JTextField meterField = new JTextField();
        JTextField phoneField = new JTextField();
        JButton addBtn = new JButton("Add Customer");
        JButton saveCustomerBtn = new JButton("Save Changes");
        saveCustomerBtn.setEnabled(false);

        addCustomerPanel.add(new JLabel("Name:")); addCustomerPanel.add(nameField);
        addCustomerPanel.add(new JLabel("Address:")); addCustomerPanel.add(addrField);
        addCustomerPanel.add(new JLabel("Meter No:")); addCustomerPanel.add(meterField);
        addCustomerPanel.add(new JLabel("Phone:")); addCustomerPanel.add(phoneField);
        addCustomerPanel.add(addBtn); addCustomerPanel.add(saveCustomerBtn);
        customerPanel.add(addCustomerPanel, BorderLayout.SOUTH);
        tabs.add("Customers", customerPanel);

        addBtn.addActionListener(e -> {
            String n = nameField.getText().trim();
            if (n.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Name required");
                return;
            }
            Customer c = new Customer(nextCustomerId++, n,
                    addrField.getText().trim(),
                    meterField.getText().trim(),
                    phoneField.getText().trim());
            customers.put(c.id, c);
            refreshCustomers();
            updateCustomerCombo();
            clearCustomerForm(nameField, addrField, meterField, phoneField);
        });

        JButton editCustomerBtn = new JButton("Edit Selected");
        customerPanel.add(editCustomerBtn, BorderLayout.NORTH);

        editCustomerBtn.addActionListener(e -> {
            int selectedRow = customerTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Select a customer to edit.");
                return;
            }
            int customerId = (int) customerModel.getValueAt(selectedRow, 0);
            Customer customer = customers.get(customerId);
            if (customer != null) {
                nameField.setText(customer.name);
                addrField.setText(customer.address);
                meterField.setText(customer.meterNumber);
                phoneField.setText(customer.phone);

                addBtn.setEnabled(false);
                saveCustomerBtn.setEnabled(true);

                saveCustomerBtn.addActionListener(saveEvent -> {
                    customer.name = nameField.getText().trim();
                    customer.address = addrField.getText().trim();
                    customer.meterNumber = meterField.getText().trim();
                    customer.phone = phoneField.getText().trim();
                    refreshCustomers();
                    updateCustomerCombo();
                    clearCustomerForm(nameField, addrField, meterField, phoneField);
                    addBtn.setEnabled(true);
                    saveCustomerBtn.setEnabled(false);
                });
            }
        });

        // ---------------- Bills Tab ----------------
        JPanel billPanel = new JPanel(new BorderLayout());
        billModel = new DefaultTableModel(new Object[]{"Bill ID", "Customer", "Month", "Units", "Total"}, 0);
        JTable billTable = new JTable(billModel);
        billPanel.add(new JScrollPane(billTable), BorderLayout.CENTER);

        JPanel billForm = new JPanel(new GridLayout(4, 2, 5, 5));
        JTextField monthField = new JTextField();
        JTextField unitsField = new JTextField();
        JButton genBtn = new JButton("Generate Bill");
        JButton saveBillBtn = new JButton("Save Changes");
        saveBillBtn.setEnabled(false);

        billForm.add(new JLabel("Customer:")); billForm.add(custCombo);
        billForm.add(new JLabel("Month:")); billForm.add(monthField);
        billForm.add(new JLabel("Units:")); billForm.add(unitsField);
        billForm.add(genBtn); billForm.add(saveBillBtn);
        billPanel.add(billForm, BorderLayout.SOUTH);
        tabs.add("Bills", billPanel);

        genBtn.addActionListener(e -> {
            Customer c = (Customer) custCombo.getSelectedItem();
            if (c == null) {
                JOptionPane.showMessageDialog(this, "No customer selected");
                return;
            }
            try {
                int units = Integer.parseInt(unitsField.getText().trim());
                Bill b = new Bill(nextBillId++, c.id, monthField.getText().trim(), units);
                b.calculate();
                bills.add(b);
                refreshBills();
                JOptionPane.showMessageDialog(this, b.toInvoiceText(c), "Bill Generated", JOptionPane.INFORMATION_MESSAGE);
                clearBillForm(monthField, unitsField);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid input: " + ex.getMessage());
            }
        });

        JButton editBillBtn = new JButton("Edit Selected");
        billPanel.add(editBillBtn, BorderLayout.NORTH);

        editBillBtn.addActionListener(e -> {
            int selectedRow = billTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Select a bill to edit.");
                return;
            }
            int billId = (int) billModel.getValueAt(selectedRow, 0);
            Bill bill = findBill(billId);
            if (bill != null) {
                custCombo.setSelectedItem(customers.get(bill.customerId));
                monthField.setText(bill.month);
                unitsField.setText(String.valueOf(bill.units));
                genBtn.setEnabled(false);
                saveBillBtn.setEnabled(true);

                saveBillBtn.addActionListener(saveEvent -> {
                    try {
                        Customer c = (Customer) custCombo.getSelectedItem();
                        bill.customerId = c.id;
                        bill.month = monthField.getText().trim();
                        bill.units = Integer.parseInt(unitsField.getText().trim());
                        bill.calculate();
                        refreshBills();
                        clearBillForm(monthField, unitsField);
                        genBtn.setEnabled(true);
                        saveBillBtn.setEnabled(false);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this, "Invalid input: " + ex.getMessage());
                    }
                });
            }
        });

        // ---------------- Menu ----------------
        JMenuBar mb = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem save = new JMenuItem("Save Data");
        JMenuItem load = new JMenuItem("Load Data");
        JMenuItem export = new JMenuItem("Export Selected Bill");
        JMenuItem exit = new JMenuItem("Exit");
        file.add(save); file.add(load); file.add(export); file.add(exit);
        mb.add(file);
        setJMenuBar(mb);

        save.addActionListener(e -> saveAll());
        load.addActionListener(e -> { loadAll(); refreshCustomers(); refreshBills(); updateCustomerCombo(); });
        exit.addActionListener(e -> { saveAll(); System.exit(0); });

        export.addActionListener(e -> {
            int row = billTable.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "Select a bill first.");
                return;
            }
            int billId = (int) billModel.getValueAt(row, 0);
            Bill b = findBill(billId);
            if (b != null) {
                Customer c = customers.get(b.customerId);
                String txt = b.toInvoiceText(c);
                try {
                    String fname = "bill_" + b.billId + ".txt";
                    Files.write(Paths.get(fname), txt.getBytes());
                    JOptionPane.showMessageDialog(this, "Exported to " + fname);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage());
                }
            }
        });

        add(tabs);
        loadAll();
        refreshCustomers();
        refreshBills();
        updateCustomerCombo();
    }

    private void clearCustomerForm(JTextField n, JTextField a, JTextField m, JTextField p) {
        n.setText(""); a.setText(""); m.setText(""); p.setText("");
    }

    private void clearBillForm(JTextField m, JTextField u) {
        m.setText(""); u.setText("");
    }

    private void refreshCustomers() {
        customerModel.setRowCount(0);
        for (Customer c : customers.values())
            customerModel.addRow(new Object[]{c.id, c.name, c.address, c.meterNumber, c.phone});
    }

    private void refreshBills() {
        billModel.setRowCount(0);
        for (Bill b : bills) {
            Customer c = customers.get(b.customerId);
            billModel.addRow(new Object[]{b.billId, (c != null ? c.name : "Unknown"), b.month, b.units, b.total});
        }
    }

    private void updateCustomerCombo() {
        custCombo.removeAllItems();
        for (Customer c : customers.values()) custCombo.addItem(c);
    }

    private void saveAll() {
        saveCustomers();
        saveBills();
        JOptionPane.showMessageDialog(this, "Data saved.");
    }

    private void loadAll() {
        loadCustomers();
        loadBills();
        refreshCustomers();
        refreshBills();
    }

    private static Bill findBill(int id) {
        for (Bill b : bills) if (b.billId == id) return b;
        return null;
    }

    private static void saveCustomers() {
        java.util.List<String> lines = new ArrayList<>();
        lines.add("id,name,address,meter,phone");
        for (Customer c : customers.values()) lines.add(c.toCSV());
        try { Files.write(Paths.get(CUSTOMERS_FILE), lines); } catch (IOException e) { }
    }

    private static void saveBills() {
        java.util.List<String> lines = new ArrayList<>();
        lines.add("billId,customerId,month,units,energy,fixed,meter,tax,total");
        for (Bill b : bills) lines.add(b.toCSV());
        try { Files.write(Paths.get(BILLS_FILE), lines); } catch (IOException e) { }
    }

    private static void loadCustomers() {
        customers.clear(); nextCustomerId = 1;
        Path p = Paths.get(CUSTOMERS_FILE);
        if (!Files.exists(p)) return;
        try {
            java.util.List<String> lines = Files.readAllLines(p);
            for (int i = 1; i < lines.size(); i++) {
                Customer c = Customer.fromCSV(lines.get(i));
                customers.put(c.id, c);
                nextCustomerId = Math.max(nextCustomerId, c.id + 1);
            }
        } catch (IOException e) { }
    }

    private static void loadBills() {
        bills.clear(); nextBillId = 1;
        Path p = Paths.get(BILLS_FILE);
        if (!Files.exists(p)) return;
        try {
            java.util.List<String> lines = Files.readAllLines(p);
            for (int i = 1; i < lines.size(); i++) {
                Bill b = Bill.fromCSV(lines.get(i));
                bills.add(b);
                nextBillId = Math.max(nextBillId, b.billId + 1);
            }
        } catch (IOException e) { }
    }

    static class Customer {
        int id; String name, address, meterNumber, phone;
        Customer(int id, String n, String a, String m, String p) {
            this.id=id; name=n; address=a; meterNumber=m; phone=p;
        }
        public String toString() { return id + ": " + name; }
        String toCSV() { return id + "," + name + "," + address + "," + meterNumber + "," + phone; }
        static Customer fromCSV(String l) {
            String[] t = l.split(",", -1);
            return new Customer(Integer.parseInt(t[0]), t[1], t[2], t[3], t[4]);
        }
    }

    static class Bill {
        int billId, customerId, units; String month;
        BigDecimal energyCharge, fixedCharge, meterRent, tax, total;
        Bill(int id, int cid, String m, int u) { billId=id; customerId=cid; month=m; units=u; }
        void calculate() {
            energyCharge = calc(units);
            fixedCharge=FIXED_CHARGE; meterRent=METER_RENT;
            BigDecimal sub = energyCharge.add(fixedCharge).add(meterRent);
            tax = sub.multiply(TAX_RATE);
            total = sub.add(tax);
            energyCharge=energyCharge.setScale(2, RoundingMode.HALF_UP);
            fixedCharge=fixedCharge.setScale(2, RoundingMode.HALF_UP);
            meterRent=meterRent.setScale(2, RoundingMode.HALF_UP);
            tax=tax.setScale(2, RoundingMode.HALF_UP);
            total=total.setScale(2, RoundingMode.HALF_UP);
        }
        private BigDecimal calc(int u) {
            BigDecimal ch=BigDecimal.ZERO; int r=u;
            for(int i=0;i<SLAB_LIMITS.length;i++){
                int take=Math.min(r,SLAB_LIMITS[i]);
                if(take>0){ ch=ch.add(BigDecimal.valueOf(take).multiply(SLAB_RATES[i])); r-=take; }
            }
            if(r>0) ch=ch.add(BigDecimal.valueOf(r).multiply(RATE_ABOVE));
            return ch;
        }
        String toCSV(){ return billId+","+customerId+","+month+","+units+","+energyCharge+","+fixedCharge+","+meterRent+","+tax+","+total; }
        static Bill fromCSV(String l){
            String[] t=l.split(",");
            Bill b=new Bill(Integer.parseInt(t[0]),Integer.parseInt(t[1]),t[2],Integer.parseInt(t[3]));
            b.energyCharge=new BigDecimal(t[4]); b.fixedCharge=new BigDecimal(t[5]);
            b.meterRent=new BigDecimal(t[6]); b.tax=new BigDecimal(t[7]); b.total=new BigDecimal(t[8]);
            return b;
        }
        String toInvoiceText(Customer c){
            return "---- ELECTRICITY BILL ----\n"+
                    "Bill ID: "+billId+"\nMonth: "+month+"\n\n"+
                    "Customer: "+c.name+"\nAddress: "+c.address+"\nMeter: "+c.meterNumber+"\nPhone: "+c.phone+"\n\n"+
                    "Units: "+units+"\nEnergy: "+energyCharge+"\nFixed: "+fixedCharge+"\nMeter Rent: "+meterRent+
                    "\nTax: "+tax+"\n----------------------\nTOTAL: "+total+"\n";
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ElectricityBillSystemGUI().setVisible(true));
    }
}
