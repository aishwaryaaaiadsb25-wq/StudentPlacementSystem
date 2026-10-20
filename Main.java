
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class Main {
    private static final String URL = "jdbc:mysql://localhost:3306/";
    private static final String DB = "placement_db";
    private static final String USER = "root";
    private static final String PASSWORD = System.getenv("DB_PASSWORD");

    private static final Scanner sc = new Scanner(System.in);
    private static final ArrayList<Integer> studentIds = new ArrayList<>();
    private static final HashSet<Integer> uniqueIds = new HashSet<>();
    private static final HashMap<String, Integer> departmentCount = new HashMap<>();

    private static Connection con;

    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection server = DriverManager.getConnection(URL, USER, PASSWORD);
                 Statement st = server.createStatement()) {
                st.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DB);
            }

            con = DriverManager.getConnection(URL + DB, USER, PASSWORD);
            createTables();
            refreshCollections();

            System.out.println("\n==================================");
            System.out.println(" STUDENT PLACEMENT MANAGEMENT");
            System.out.println("==================================");

            menu();

        } catch (ClassNotFoundException e) {
            System.out.println("MySQL Connector JAR is not configured.");
            System.out.println("Check the lib folder and VS Code settings.");
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        } finally {
            try {
                if (con != null) con.close();
            } catch (SQLException ignored) {}
            sc.close();
        }
    }

    private static void createTables() throws SQLException {
        String students = """
            CREATE TABLE IF NOT EXISTS students (
                id INT PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                department VARCHAR(100) NOT NULL,
                email VARCHAR(150) UNIQUE NOT NULL,
                cgpa DECIMAL(4,2) NOT NULL,
                graduation_year INT NOT NULL
            )
            """;

        String applications = """
            CREATE TABLE IF NOT EXISTS applications (
                application_id INT AUTO_INCREMENT PRIMARY KEY,
                student_id INT NOT NULL,
                company VARCHAR(150) NOT NULL,
                job_role VARCHAR(150) NOT NULL,
                minimum_cgpa DECIMAL(4,2) NOT NULL,
                status VARCHAR(30) DEFAULT 'Applied',
                FOREIGN KEY (student_id) REFERENCES students(id)
                    ON DELETE CASCADE
            )
            """;

        try (Statement st = con.createStatement()) {
            st.executeUpdate(students);
            st.executeUpdate(applications);
        }
    }

    private static void menu() {
        while (true) {
            System.out.println("\n========== MAIN MENU ==========");
            System.out.println("1. Add Student");
            System.out.println("2. View All Students");
            System.out.println("3. Search Student");
            System.out.println("4. Update Student");
            System.out.println("5. Delete Student");
            System.out.println("6. Add Placement Opportunity");
            System.out.println("7. View Placement Opportunities");
            System.out.println("8. Update Application Status");
            System.out.println("9. Placement Statistics");
            System.out.println("0. Exit");
            System.out.print("Enter choice: ");

            int choice = readInt();

            try {
                switch (choice) {
                    case 1 -> addStudent();
                    case 2 -> viewStudents();
                    case 3 -> searchStudent();
                    case 4 -> updateStudent();
                    case 5 -> deleteStudent();
                    case 6 -> addApplication();
                    case 7 -> viewApplications();
                    case 8 -> updateStatus();
                    case 9 -> showStatistics();
                    case 0 -> {
                        System.out.println("Application closed.");
                        return;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            } catch (SQLException e) {
                System.out.println("Operation failed: " + e.getMessage());
            }
        }
    }

    private static int readInt() {
        while (!sc.hasNextInt()) {
            System.out.print("Enter a valid number: ");
            sc.next();
        }
        int value = sc.nextInt();
        sc.nextLine();
        return value;
    }

    private static double readDouble() {
        while (!sc.hasNextDouble()) {
            System.out.print("Enter a valid number: ");
            sc.next();
        }
        double value = sc.nextDouble();
        sc.nextLine();
        return value;
    }

    private static String readText(String message) {
        System.out.print(message);
        return sc.nextLine().trim();
    }

    private static void refreshCollections() throws SQLException {
        studentIds.clear();
        uniqueIds.clear();
        departmentCount.clear();

        String sql = "SELECT id, department FROM students";
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String dept = rs.getString("department");
                studentIds.add(id);
                uniqueIds.add(id);
                departmentCount.merge(dept, 1, Integer::sum);
            }
        }
    }

    private static void addStudent() throws SQLException {
        System.out.println("\n--- Add Student ---");
        System.out.print("Student ID: ");
        int id = readInt();

        if (uniqueIds.contains(id)) {
            System.out.println("This student ID already exists.");
            return;
        }

        String name = readText("Name: ");
        String dept = readText("Department: ");
        String email = readText("Email: ");
        System.out.print("CGPA (0-10): ");
        double cgpa = readDouble();
        System.out.print("Graduation year: ");
        int year = readInt();

        if (name.isBlank() || dept.isBlank() || email.isBlank()
                || cgpa < 0 || cgpa > 10 || year < 2000 || year > 2100) {
            System.out.println("Please enter valid student details.");
            return;
        }

        String sql = """
            INSERT INTO students
            (id, name, department, email, cgpa, graduation_year)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.setString(3, dept);
            ps.setString(4, email);
            ps.setDouble(5, cgpa);
            ps.setInt(6, year);
            ps.executeUpdate();
        }

        refreshCollections();
        System.out.println("Student added successfully.");
    }

    private static void viewStudents() throws SQLException {
        String sql = "SELECT * FROM students ORDER BY id";

        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- Student Records ---");
            boolean found = false;

            while (rs.next()) {
                found = true;
                printStudent(rs);
            }

            if (!found) System.out.println("No student records found.");
        }
    }

    private static void printStudent(ResultSet rs) throws SQLException {
        System.out.printf(
            "ID: %d | Name: %s | Department: %s | Email: %s | CGPA: %.2f | Year: %d%n",
            rs.getInt("id"), rs.getString("name"),
            rs.getString("department"), rs.getString("email"),
            rs.getDouble("cgpa"), rs.getInt("graduation_year")
        );
    }

    private static void searchStudent() throws SQLException {
        System.out.print("Enter student ID: ");
        int id = readInt();

        String sql = "SELECT * FROM students WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) printStudent(rs);
                else System.out.println("Student not found.");
            }
        }
    }

    // FIXED: display current database records and use the exact ID from MySQL.
    private static void updateStudent() throws SQLException {
        System.out.println("\n--- Update Student ---");
        viewStudents();

        System.out.print("\nEnter the exact student ID shown above: ");
        int id = readInt();

        String findSql = "SELECT * FROM students WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(findSql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Student ID " + id + " not found.");
                    System.out.println("Please use an ID from Student Records.");
                    return;
                }
                System.out.println("\nCurrent details:");
                printStudent(rs);
            }
        }

        String name = readText("New name: ");
        String dept = readText("New department: ");
        String email = readText("New email: ");
        System.out.print("New CGPA (0-10): ");
        double cgpa = readDouble();
        System.out.print("New graduation year: ");
        int year = readInt();

        if (name.isBlank() || dept.isBlank() || email.isBlank()
                || cgpa < 0 || cgpa > 10 || year < 2000 || year > 2100) {
            System.out.println("Please enter valid details.");
            return;
        }

        String updateSql = """
            UPDATE students
            SET name = ?, department = ?, email = ?,
                cgpa = ?, graduation_year = ?
            WHERE id = ?
            """;

        try (PreparedStatement ps = con.prepareStatement(updateSql)) {
            ps.setString(1, name);
            ps.setString(2, dept);
            ps.setString(3, email);
            ps.setDouble(4, cgpa);
            ps.setInt(5, year);
            ps.setInt(6, id);

            int updated = ps.executeUpdate();
            if (updated > 0) {
                refreshCollections();
                System.out.println("Student updated successfully.");
            } else {
                System.out.println("Student was not updated.");
            }
        }
    }

    private static void deleteStudent() throws SQLException {
        System.out.print("Enter student ID to delete: ");
        int id = readInt();

        String sql = "DELETE FROM students WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            if (ps.executeUpdate() > 0) {
                refreshCollections();
                System.out.println("Student deleted successfully.");
            } else {
                System.out.println("Student not found.");
            }
        }
    }

    private static void addApplication() throws SQLException {
        System.out.println("\n--- Add Placement Opportunity ---");
        System.out.print("Student ID: ");
        int studentId = readInt();

        double studentCgpa;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT cgpa FROM students WHERE id = ?")) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Student not found. Add student first.");
                    return;
                }
                studentCgpa = rs.getDouble("cgpa");
            }
        }

        String company = readText("Company name: ");
        String role = readText("Job role: ");
        System.out.print("Minimum required CGPA: ");
        double minimum = readDouble();

        if (company.isBlank() || role.isBlank() || minimum < 0 || minimum > 10) {
            System.out.println("Invalid details.");
            return;
        }

        String status = studentCgpa >= minimum ? "Eligible" : "Not Eligible";
        String sql = """
            INSERT INTO applications
            (student_id, company, job_role, minimum_cgpa, status)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setString(2, company);
            ps.setString(3, role);
            ps.setDouble(4, minimum);
            ps.setString(5, status);
            ps.executeUpdate();
        }

        System.out.println("Opportunity saved. Eligibility: " + status);
    }

    private static void viewApplications() throws SQLException {
        String sql = """
            SELECT a.application_id, s.name, a.company, a.job_role,
                   a.minimum_cgpa, a.status
            FROM applications a
            JOIN students s ON s.id = a.student_id
            ORDER BY a.application_id
            """;

        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- Placement Opportunities ---");
            boolean found = false;

            while (rs.next()) {
                found = true;
                System.out.printf(
                    "Application: %d | Student: %s | Company: %s | Role: %s | Minimum CGPA: %.2f | Status: %s%n",
                    rs.getInt("application_id"), rs.getString("name"),
                    rs.getString("company"), rs.getString("job_role"),
                    rs.getDouble("minimum_cgpa"), rs.getString("status")
                );
            }

            if (!found) System.out.println("No opportunities found.");
        }
    }

    private static void updateStatus() throws SQLException {
        System.out.print("Application ID: ");
        int id = readInt();

        String status = readText(
            "New status (Applied/Shortlisted/Selected/Rejected): ");

        Set<String> valid = Set.of("Applied", "Shortlisted", "Selected", "Rejected");
        if (!valid.contains(status)) {
            System.out.println("Invalid status. Use the listed options.");
            return;
        }

        String sql = "UPDATE applications SET status = ? WHERE application_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            System.out.println(ps.executeUpdate() > 0
                    ? "Application status updated."
                    : "Application not found.");
        }
    }

    private static void showStatistics() throws SQLException {
        refreshCollections();

        System.out.println("\n--- Placement Statistics ---");
        System.out.println("Total students: " + studentIds.size());
        System.out.println("Unique student IDs: " + uniqueIds.size());

        System.out.println("\nDepartment-wise student count:");
        if (departmentCount.isEmpty()) {
            System.out.println("No student data available.");
        } else {
            departmentCount.forEach((department, count) ->
                System.out.println(department + ": " + count));
        }

        String sql = "SELECT status, COUNT(*) AS total FROM applications GROUP BY status";
        System.out.println("\nApplication status count:");

        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println(rs.getString("status") + ": " + rs.getInt("total"));
            }
            if (!found) System.out.println("No placement applications yet.");
        }
    }
}
