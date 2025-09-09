import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.util.*;

/**
 * Simple Electricity Bill System (console-based)
 * Save as: ElectricityBillSystem.java
 *
 * Java 8+ recommended.
 */
public class ElectricityBillSystem {

    // Files for persistence
    private static final String CUSTOMERS_FILE = "customers.csv";
    private static final String BILLS_FILE = "bills.csv";

    // In-memory stores
    private static final Map<Integer, Customer> customers = new HashMap<>();
    private static final List<Bill> bills = new ArrayList<>();

    // ID counters
    private static int nextCustomerId = 1;
    private static int nextBillId = 1;

    // Slab configuration (edit to change tariffs)
    // slabLimits = first slabs (units), last slab is "rest"
    private static final int[] SLAB_LIMITS = {50, 100, 100}; // last one handled separately
    private static final BigDecimal[] SLAB_RATES = {
            new BigDecimal("3.00"),   // first 50 units
            new BigDecimal("5.50"),   // next 100 units
            new BigDecimal("7.00")    // next 100 units
    };
    private static final BigDecimal RATE_ABOVE = new BigDecimal("9.00"); // units above sum(SLAB_LIMITS)

    private static final BigDecimal FIXED_CHARGE = new BigDecimal("50.00");
    private static final BigDecimal METER_RENT = new BigDecimal("20.00");
    private static final BigDecimal TAX_RATE = new BigDecimal("0.05"); // 5%

    public static void main(String[] args) {
        loadAllData(); // try to restore from CSV if present
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.println("\n==== Electricity Bill System ====");
            System.out.println("1. Add customer");
            System.out.println("2. List customers");
            System.out.println("3. Generate bill");
            System.out.println("4. List bills");
            System.out.println("5. View bill (by id)");
            System.out.println("6. Export bill to text file");
            System.out.println("7. Save data");
            System.out.println("8. Load data");
            System.out.println("9. Exit");
            System.out.print("Choose an option: ");
            String choice = sc.nextLine().trim();

            switch (choice) {
                case "1": addCustomer(sc); break;
                case "2": listCustomers(); break;
                case "3": generateBill(sc); break;
                case "4": listBills(); break;
                case "5": viewBill(sc); break;
                case "6": exportBill(sc); break;
                case "7": saveAllData(); break;
                case "8": loadAllData(); break;
                case "9": saveAllData(); System.out.println("Goodbye!"); return;
                default: System.out.println("Unknown option. Try again.");
            }
        }
    }

    // -------------------- Customer --------------------
    static class Customer {
        int id;
        String name;
        String address;
        String meterNumber;
        String phone;

        Customer(int id, String name, String address, String meterNumber, String phone) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.meterNumber = meterNumber;
            this.phone = phone;
        }

        String toCSV() {
            // simple CSV; avoid commas in fields
            return id + "," + escape(name) + "," + escape(address) + "," + escape(meterNumber) + "," + escape(phone);
        }

        static Customer fromCSV(String line) {
            String[] t = line.split(",", -1);
            int id = Integer.parseInt(t[0]);
            return new Customer(id, unescape(t[1]), unescape(t[2]), unescape(t[3]), unescape(t[4]));
        }

        private static String escape(String s) {
            return s.replace("\n", " ").replace("\r", " ").replace(",", " "); // crude
        }

        private static String unescape(String s) {
            return s;
        }

        @Override
        public String toString() {
            return String.format("[%d] %s, Addr: %s, Meter: %s, Phone: %s", id, name, address, meterNumber, phone);
        }
    }

    // -------------------- Bill --------------------
    static class Bill {
        int billId;
        int customerId;
        String month; // e.g., "2025-09" or "September 2025"
        int units;
        BigDecimal energyCharge;
        BigDecimal fixedCharge;
        BigDecimal meterRent;
        BigDecimal tax;
        BigDecimal total;

        Bill(int billId, int customerId, String month, int units) {
            this.billId = billId;
            this.customerId = customerId;
            this.month = month;
            this.units = units;
        }

        void calculate() {
            energyCharge = calculateEnergyCharge(units);
            fixedCharge = FIXED_CHARGE;
            meterRent = METER_RENT;
            BigDecimal subtotal = energyCharge.add(fixedCharge).add(meterRent);
            tax = subtotal.multiply(TAX_RATE);
            total = subtotal.add(tax);

            // round to 2 decimals
            energyCharge = energyCharge.setScale(2, RoundingMode.HALF_UP);
            fixedCharge = fixedCharge.setScale(2, RoundingMode.HALF_UP);
            meterRent = meterRent.setScale(2, RoundingMode.HALF_UP);
            tax = tax.setScale(2, RoundingMode.HALF_UP);
            total = total.setScale(2, RoundingMode.HALF_UP);
        }

        String toCSV() {
            return billId + "," + customerId + "," + month + "," + units + "," +
                    energyCharge.toPlainString() + "," + fixedCharge.toPlainString() + "," +
                    meterRent.toPlainString() + "," + tax.toPlainString() + "," + total.toPlainString();
        }

        static Bill fromCSV(String line) {
            String[] t = line.split(",", -1);
            Bill b = new Bill(Integer.parseInt(t[0]), Integer.parseInt(t[1]), t[2], Integer.parseInt(t[3]));
            b.energyCharge = new BigDecimal(t[4]);
            b.fixedCharge = new BigDecimal(t[5]);
            b.meterRent = new BigDecimal(t[6]);
            b.tax = new BigDecimal(t[7]);
            b.total = new BigDecimal(t[8]);
            return b;
        }

        String toInvoiceText(Customer c) {
            StringBuilder sb = new StringBuilder();
            sb.append("-------- ELECTRICITY BILL --------\n");
            sb.append("Bill ID: ").append(billId).append("\n");
            sb.append("Month: ").append(month).append("\n\n");
            sb.append("Customer: ").append(c.name).append("\n");
            sb.append("Address: ").append(c.address).append("\n");
            sb.append("Meter No: ").append(c.meterNumber).append("\n");
            sb.append("Phone: ").append(c.phone).append("\n\n");
            sb.append(String.format("Units Consumed: %d\n", units));
            sb.append(String.format("Energy Charge: %s\n", energyCharge.toPlainString()));
            sb.append(String.format("Fixed Charge: %s\n", fixedCharge.toPlainString()));
            sb.append(String.format("Meter Rent: %s\n", meterRent.toPlainString()));
            sb.append(String.format("Tax (%.2f%%): %s\n", TAX_RATE.multiply(new BigDecimal("100")).doubleValue(), tax.toPlainString()));
            sb.append("----------------------------------\n");
            sb.append(String.format("Total Payable: %s\n", total.toPlainString()));
            sb.append("----------------------------------\n");
            return sb.toString();
        }

        private BigDecimal calculateEnergyCharge(int units) {
            BigDecimal charge = BigDecimal.ZERO;
            int remaining = units;
            for (int i = 0; i < SLAB_LIMITS.length; i++) {
                int take = Math.min(remaining, SLAB_LIMITS[i]);
                if (take > 0) {
                    charge = charge.add(BigDecimal.valueOf(take).multiply(SLAB_RATES[i]));
                    remaining -= take;
                }
            }
            if (remaining > 0) {
                charge = charge.add(BigDecimal.valueOf(remaining).multiply(RATE_ABOVE));
            }
            return charge;
        }
    }

    // -------------------- Menu Actions --------------------

    private static void addCustomer(Scanner sc) {
        System.out.println("--- Add customer ---");
        System.out.print("Name: ");
        String name = sc.nextLine().trim();
        System.out.print("Address: ");
        String address = sc.nextLine().trim();
        System.out.print("Meter number: ");
        String meter = sc.nextLine().trim();
        System.out.print("Phone: ");
        String phone = sc.nextLine().trim();

        Customer c = new Customer(nextCustomerId++, name, address, meter, phone);
        customers.put(c.id, c);
        System.out.println("Added: " + c);
    }

    private static void listCustomers() {
        System.out.println("--- Customers ---");
        if (customers.isEmpty()) {
            System.out.println("No customers.");
            return;
        }
        customers.values().stream()
                .sorted(Comparator.comparingInt(a -> a.id))
                .forEach(System.out::println);
    }

    private static void generateBill(Scanner sc) {
        System.out.println("--- Generate bill ---");
        listCustomers();
        System.out.print("Enter customer ID: ");
        String cs = sc.nextLine().trim();
        try {
            int cid = Integer.parseInt(cs);
            Customer c = customers.get(cid);
            if (c == null) {
                System.out.println("Customer not found.");
                return;
            }
            System.out.print("Enter billing month (e.g., 2025-09 or September 2025): ");
            String month = sc.nextLine().trim();
            System.out.print("Enter units consumed (integer): ");
            int units = Integer.parseInt(sc.nextLine().trim());
            Bill b = new Bill(nextBillId++, cid, month, units);
            b.calculate();
            bills.add(b);
            System.out.println("Bill generated:\n");
            System.out.println(b.toInvoiceText(c));
        } catch (NumberFormatException e) {
            System.out.println("Invalid number input.");
        }
    }

    private static void listBills() {
        System.out.println("--- Bills ---");
        if (bills.isEmpty()) {
            System.out.println("No bills.");
            return;
        }
        for (Bill b : bills) {
            Customer c = customers.get(b.customerId);
            System.out.printf("[%d] %s - %s - %s\n", b.billId, (c != null ? c.name : "Unknown"), b.month, b.total.toPlainString());
        }
    }

    private static void viewBill(Scanner sc) {
        System.out.print("Enter bill ID: ");
        String s = sc.nextLine().trim();
        try {
            int id = Integer.parseInt(s);
            Bill bb = findBillById(id);
            if (bb == null) {
                System.out.println("Bill not found.");
                return;
            }
            Customer c = customers.get(bb.customerId);
            System.out.println(bb.toInvoiceText(c != null ? c : new Customer(0, "Unknown", "", "", "")));
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        }
    }

    private static void exportBill(Scanner sc) {
        System.out.print("Enter bill ID to export: ");
        String s = sc.nextLine().trim();
        try {
            int id = Integer.parseInt(s);
            Bill bb = findBillById(id);
            if (bb == null) {
                System.out.println("Bill not found.");
                return;
            }
            Customer c = customers.get(bb.customerId);
            String text = bb.toInvoiceText(c != null ? c : new Customer(0, "Unknown", "", "", ""));
            String filename = "bill_" + bb.billId + ".txt";
            Files.write(Paths.get(filename), text.getBytes());
            System.out.println("Exported to " + filename);
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        } catch (IOException e) {
            System.out.println("Failed to write file: " + e.getMessage());
        }
    }

    // -------------------- Persistence --------------------

    private static void saveAllData() {
        saveCustomers();
        saveBills();
        System.out.println("Data saved.");
    }

    private static void loadAllData() {
        loadCustomers();
        loadBills();
        System.out.println("Data loaded (if files existed).");
    }

    private static void saveCustomers() {
        List<String> lines = new ArrayList<>();
        lines.add("id,name,address,meter,phone");
        customers.values().stream()
                .sorted(Comparator.comparingInt(a -> a.id))
                .forEach(c -> lines.add(c.toCSV()));
        try {
            Files.write(Paths.get(CUSTOMERS_FILE), lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.out.println("Failed to save customers: " + e.getMessage());
        }
    }

    private static void saveBills() {
        List<String> lines = new ArrayList<>();
        lines.add("billId,customerId,month,units,energyCharge,fixedCharge,meterRent,tax,total");
        bills.stream()
                .sorted(Comparator.comparingInt(b -> b.billId))
                .forEach(b -> lines.add(b.toCSV()));
        try {
            Files.write(Paths.get(BILLS_FILE), lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.out.println("Failed to save bills: " + e.getMessage());
        }
    }

    private static void loadCustomers() {
        customers.clear();
        Path p = Paths.get(CUSTOMERS_FILE);
        if (!Files.exists(p)) return;
        try {
            List<String> lines = Files.readAllLines(p);
            for (int i = 1; i < lines.size(); i++) {
                String l = lines.get(i).trim();
                if (l.isEmpty()) continue;
                Customer c = Customer.fromCSV(l);
                customers.put(c.id, c);
                nextCustomerId = Math.max(nextCustomerId, c.id + 1);
            }
        } catch (IOException e) {
            System.out.println("Failed to load customers: " + e.getMessage());
        }
    }

    private static void loadBills() {
        bills.clear();
        Path p = Paths.get(BILLS_FILE);
        if (!Files.exists(p)) return;
        try {
            List<String> lines = Files.readAllLines(p);
            for (int i = 1; i < lines.size(); i++) {
                String l = lines.get(i).trim();
                if (l.isEmpty()) continue;
                Bill b = Bill.fromCSV(l);
                bills.add(b);
                nextBillId = Math.max(nextBillId, b.billId + 1);
            }
        } catch (IOException e) {
            System.out.println("Failed to load bills: " + e.getMessage());
        }
    }

    private static Bill findBillById(int id) {
        for (Bill b : bills) if (b.billId == id) return b;
        return null;
    }
}
