import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*; // --- NEW: JDBC Import ---
import java.util.ArrayList;

// ==========================================
// CUSTOM EXCEPTION
// ==========================================
class InvalidLoginException extends Exception {
    public InvalidLoginException(String message) {
        super(message);
    }
}

// ==========================================
// ABSTRACTION & ENCAPSULATION
// ==========================================
abstract class Account {
    private String username;
    private String password;
    private double avgRating;
    private int totalPoints = 0;
    private int ratingCount = 0;

    public Account(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public void addRating(int stars) {
        totalPoints += stars;
        ratingCount++;
        avgRating = (double) totalPoints / ratingCount;
    }
    
    public void setAvgRating(double rating) { this.avgRating = rating; }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public double getAvgRating() { return avgRating; }

    // ABSTRACTION: subclass must define its panel
    public abstract JPanel buildDashboard(RideSystem system, JFrame frame);
}

// ==========================================
// INHERITANCE + POLYMORPHISM
// ==========================================
class User extends Account {
    public User(String username, String password) {
        super(username, password);
    }

    @Override
    public JPanel buildDashboard(RideSystem system, JFrame frame) {
        return new UserDashboard(this, system, frame);
    }
}

class Driver extends Account {
    public Driver(String username, String password) {
        super(username, password);
    }

    @Override
    public JPanel buildDashboard(RideSystem system, JFrame frame) {
        return new DriverDashboard(this, system, frame);
    }
}

// ==========================================
// ENCAPSULATION - Ride
// ==========================================
class Ride {
    private int id;
    private String pick;
    private String drop;
    private double fare;
    private String status;
    private String user;
    private String driver;
    private boolean userRated = false;
    private boolean driverRated = false;

    public Ride(int id, String pick, String drop, double fare, String user) {
        this.id = id;
        this.pick = pick;
        this.drop = drop;
        this.fare = fare;
        this.status = "Pending";
        this.user = user;
        this.driver = "None";
    }

    public int getId() { return id; }
    public String getPick() { return pick; }
    public String getDrop() { return drop; }
    public double getFare() { return fare; }
    public void setStatus(String status) { this.status = status; }
    public String getStatus() { return status; }
    public String getUsername() { return user; }
    public String getDriverName() { return driver; }
    public void setDriverName(String driver) { this.driver = driver; }
    public boolean isUserRated() { return userRated; }
    public void setUserRated(boolean b) { this.userRated = b; }
    public boolean isDriverRated() { return driverRated; }
    public void setDriverRated(boolean b) { this.driverRated = b; }
}

// ==========================================
// RIDE SYSTEM (Business Logic - NOW USING JDBC)
// ==========================================
class RideSystem {
    
    // --- NEW: Centralized Database Connection Method ---
    private Connection getConnection() throws SQLException {
        String url = "jdbc:mysql://localhost:3306/ridebookingsystem";
        String user = "Enter your username ";
        String password = "Enter your DB password"; // Your DB password
        return DriverManager.getConnection(url, user, password);
    }

    public User userLogin(String username, String password) throws InvalidLoginException {
        // --- NEW: Querying the Database for User Login ---
        try (Connection con = getConnection();
             PreparedStatement pst = con.prepareStatement("SELECT * FROM users WHERE username = ? AND password = ?")) {
            pst.setString(1, username);
            pst.setString(2, password);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                User u = new User(rs.getString("username"), rs.getString("password"));
                u.setAvgRating(rs.getDouble("avg_rating"));
                return u;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        throw new InvalidLoginException("Incorrect User credentials.");
    }

    public Driver driverLogin(String username, String password) throws InvalidLoginException {
        // --- NEW: Querying the Database for Driver Login ---
        try (Connection con = getConnection();
             PreparedStatement pst = con.prepareStatement("SELECT * FROM drivers WHERE username = ? AND password = ?")) {
            pst.setString(1, username);
            pst.setString(2, password);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                Driver d = new Driver(rs.getString("username"), rs.getString("password"));
                d.setAvgRating(rs.getDouble("avg_rating"));
                return d;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        throw new InvalidLoginException("Incorrect Driver credentials.");
    }

    public void requestRide(String pickup, String drop, double fare, String username) {
        // --- NEW: Inserting Ride into Database ---
        try (Connection con = getConnection()) {
            int userId = 0;
            PreparedStatement userStmt = con.prepareStatement("SELECT user_id FROM users WHERE username = ?");
            userStmt.setString(1, username);
            ResultSet rsUser = userStmt.executeQuery();
            if (rsUser.next()) userId = rsUser.getInt("user_id");

            String rideQuery = "INSERT INTO rides (user_id, fare, status_id) VALUES (?, ?, 1)";
            PreparedStatement rideStmt = con.prepareStatement(rideQuery, Statement.RETURN_GENERATED_KEYS);
            rideStmt.setInt(1, userId);
            rideStmt.setDouble(2, fare);
            rideStmt.executeUpdate();

            ResultSet keys = rideStmt.getGeneratedKeys();
            if (keys.next()) {
                int newId = keys.getInt(1);
                PreparedStatement pStmt = con.prepareStatement("INSERT INTO pickup_locations (ride_id, pickup_location) VALUES (?, ?)");
                pStmt.setInt(1, newId); pStmt.setString(2, pickup); pStmt.executeUpdate();

                PreparedStatement dStmt = con.prepareStatement("INSERT INTO drop_locations (ride_id, drop_location) VALUES (?, ?)");
                dStmt.setInt(1, newId); dStmt.setString(2, drop); dStmt.executeUpdate();
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public ArrayList<Ride> getAllRides() {
        // --- NEW: Fetching ALL Rides from the Database ---
        ArrayList<Ride> dbRides = new ArrayList<>();
        try (Connection con = getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT r.ride_id, p.pickup_location, d.drop_location, r.fare, s.status_name, " +
                 "u.username as user_name, IFNULL(drv.username, 'None') as driver_name " +
                 "FROM rides r " +
                 "JOIN pickup_locations p ON r.ride_id = p.ride_id " +
                 "JOIN drop_locations d ON r.ride_id = d.ride_id " +
                 "JOIN users u ON r.user_id = u.user_id " +
                 "JOIN ride_status s ON r.status_id = s.status_id " +
                 "LEFT JOIN drivers drv ON r.driver_id = drv.driver_id ORDER BY r.ride_id DESC"
             )) {
            while (rs.next()) {
                Ride r = new Ride(
                    rs.getInt("ride_id"),
                    rs.getString("pickup_location"),
                    rs.getString("drop_location"),
                    rs.getDouble("fare"),
                    rs.getString("user_name")
                );
                r.setStatus(rs.getString("status_name"));
                r.setDriverName(rs.getString("driver_name"));
                dbRides.add(r);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return dbRides;
    }

    // --- NEW: Method to save accept/complete actions to DB ---
    public void updateRideStatusInDB(int rideId, String statusName, String driverName) {
        try (Connection con = getConnection()) {
            int statusId = 1;
            if (statusName.equals("Accepted")) statusId = 2;
            else if (statusName.equals("Completed")) statusId = 3;

            Integer driverId = null;
            if (driverName != null && !driverName.equals("None")) {
                PreparedStatement dst = con.prepareStatement("SELECT driver_id FROM drivers WHERE username=?");
                dst.setString(1, driverName);
                ResultSet drs = dst.executeQuery();
                if (drs.next()) driverId = drs.getInt(1);
            }

            PreparedStatement pst;
            if (driverId != null) {
                pst = con.prepareStatement("UPDATE rides SET status_id=?, driver_id=? WHERE ride_id=?");
                pst.setInt(1, statusId);
                pst.setInt(2, driverId);
                pst.setInt(3, rideId);
            } else {
                pst = con.prepareStatement("UPDATE rides SET status_id=? WHERE ride_id=?");
                pst.setInt(1, statusId);
                pst.setInt(2, rideId);
            }
            pst.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    
    // Kept for basic local object retrieval
    public Driver getDriver(String username) { return new Driver(username, ""); }
    public User getUser(String username) { return new User(username, ""); }
}

// ==========================================
// GUI HELPER - Shared Colors & Fonts
// ==========================================
class AppTheme {
    static final Color BG = new Color(15, 20, 40);
    static final Color CARD = new Color(25, 33, 60);
    static final Color ACCENT = new Color(99, 102, 241);
    static final Color ACCENT2 = new Color(139, 92, 246);
    static final Color SUCCESS = new Color(34, 197, 94);
    static final Color WARNING = new Color(251, 146, 60);
    static final Color TEXT = new Color(226, 232, 240);
    static final Color SUBTEXT = new Color(148, 163, 184);
    static final Color BORDER = new Color(51, 65, 85);

    static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 28);
    static final Font HEADING_FONT = new Font("Segoe UI", Font.BOLD, 16);
    static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 14);
    static final Font SMALL_FONT = new Font("Segoe UI", Font.PLAIN, 12);

    static JButton styledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        return btn;
    }

    static JTextField styledField() {
        JTextField f = new JTextField();
        f.setBackground(new Color(30, 41, 59));
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setFont(BODY_FONT);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1, true),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        return f;
    }

    static JPasswordField styledPassword() {
        JPasswordField f = new JPasswordField();
        f.setBackground(new Color(30, 41, 59));
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setFont(BODY_FONT);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1, true),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        return f;
    }

    static JLabel label(String text, Font font, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(font);
        lbl.setForeground(color);
        return lbl;
    }

    static JPanel card() {
        JPanel p = new JPanel();
        p.setBackground(CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1, true),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)));
        return p;
    }
}

// ==========================================
// LOGIN SCREEN
// ==========================================
class LoginPanel extends JPanel {
    public LoginPanel(RideSystem system, JFrame frame) {
        setLayout(new GridBagLayout());
        setBackground(AppTheme.BG);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(AppTheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AppTheme.BORDER, 1, true),
            BorderFactory.createEmptyBorder(40, 40, 40, 40)));
        card.setPreferredSize(new Dimension(400, 480));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 0, 8, 0);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0; gc.weightx = 1.0;

        // Logo/Title
        gc.gridy = 0; gc.insets = new Insets(0, 0, 30, 0);
        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        JLabel icon = AppTheme.label("", new Font("Segoe UI Emoji", Font.PLAIN, 40), AppTheme.ACCENT);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel title = AppTheme.label("RideSwift DB", AppTheme.TITLE_FONT, AppTheme.TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel sub = AppTheme.label("Connected to MySQL", AppTheme.SMALL_FONT, AppTheme.SUCCESS);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        titlePanel.add(icon);
        titlePanel.add(Box.createVerticalStrut(8));
        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(sub);
        card.add(titlePanel, gc);

        // Role toggle
        gc.gridy = 1; gc.insets = new Insets(8, 0, 8, 0);
        JPanel rolePanel = new JPanel(new GridLayout(1, 2, 4, 0));
        rolePanel.setOpaque(false);
        JToggleButton userBtn = new JToggleButton("User");
        JToggleButton driverBtn = new JToggleButton("Driver");
        ButtonGroup roleGroup = new ButtonGroup();
        styleToggle(userBtn, true);
        styleToggle(driverBtn, false);
        roleGroup.add(userBtn);
        roleGroup.add(driverBtn);
        userBtn.setSelected(true);
        rolePanel.add(userBtn);
        rolePanel.add(driverBtn);
        card.add(rolePanel, gc);

        // Username
        gc.gridy = 2;
        JLabel userLabel = AppTheme.label("Username", AppTheme.SMALL_FONT, AppTheme.SUBTEXT);
        card.add(userLabel, gc);
        gc.gridy = 3; gc.insets = new Insets(2, 0, 8, 0);
        JTextField userField = AppTheme.styledField();
        card.add(userField, gc);

        // Password
        gc.gridy = 4; gc.insets = new Insets(8, 0, 2, 0);
        JLabel passLabel = AppTheme.label("Password", AppTheme.SMALL_FONT, AppTheme.SUBTEXT);
        card.add(passLabel, gc);
        gc.gridy = 5; gc.insets = new Insets(2, 0, 20, 0);
        JPasswordField passField = AppTheme.styledPassword();
        card.add(passField, gc);

        // Login button
        gc.gridy = 6; gc.insets = new Insets(8, 0, 8, 0);
        JButton loginBtn = AppTheme.styledButton("Sign In", AppTheme.ACCENT);
        loginBtn.setPreferredSize(new Dimension(0, 44));
        card.add(loginBtn, gc);

        // Error label
        gc.gridy = 7;
        JLabel errLabel = AppTheme.label("", AppTheme.SMALL_FONT, new Color(239, 68, 68));
        errLabel.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(errLabel, gc);

        // Hint
        gc.gridy = 8;
        JLabel hint = AppTheme.label("Use any username from your DB", AppTheme.SMALL_FONT, AppTheme.SUBTEXT);
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(hint, gc);

        // Toggle button style on selection
        userBtn.addActionListener(e -> { styleToggle(userBtn, true); styleToggle(driverBtn, false); });
        driverBtn.addActionListener(e -> { styleToggle(driverBtn, true); styleToggle(userBtn, false); });

        // Login action
        ActionListener doLogin = e -> {
            String username = userField.getText().trim();
            String password = new String(passField.getPassword()).trim();
            try {
                Account acc;
                if (userBtn.isSelected()) {
                    acc = system.userLogin(username, password);
                } else {
                    acc = system.driverLogin(username, password);
                }
                JPanel dashboard = acc.buildDashboard(system, frame);
                frame.setContentPane(dashboard);
                frame.revalidate();
                frame.repaint();
            } catch (InvalidLoginException ex) {
                errLabel.setText(ex.getMessage());
            }
        };

        loginBtn.addActionListener(doLogin);
        passField.addActionListener(doLogin);

        add(card);
    }

    private void styleToggle(JToggleButton btn, boolean active) {
        btn.setBackground(active ? AppTheme.ACCENT : new Color(30, 41, 59));
        btn.setForeground(active ? Color.WHITE : AppTheme.SUBTEXT);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
    }
}

// ==========================================
// USER DASHBOARD
// ==========================================
class UserDashboard extends JPanel {
    private User user;
    private RideSystem system;
    private JFrame frame;
    private JPanel contentArea;

    public UserDashboard(User user, RideSystem system, JFrame frame) {
        this.user = user;
        this.system = system;
        this.frame = frame;
        setLayout(new BorderLayout());
        setBackground(AppTheme.BG);

        add(buildSidebar(), BorderLayout.WEST);
        contentArea = new JPanel(new BorderLayout());
        contentArea.setBackground(AppTheme.BG);
        showRequestRide();
        add(contentArea, BorderLayout.CENTER);
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(AppTheme.CARD);
        sidebar.setBorder(BorderFactory.createEmptyBorder(30, 20, 30, 20));
        sidebar.setPreferredSize(new Dimension(220, 0));

        JLabel logo = AppTheme.label("RideSwift DB", new Font("Segoe UI", Font.BOLD, 18), AppTheme.ACCENT);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(logo);
        sidebar.add(Box.createVerticalStrut(30));

        // User info card
        JPanel infoCard = AppTheme.card();
        infoCard.setLayout(new BoxLayout(infoCard, BoxLayout.Y_AXIS));
        infoCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        JLabel avatar = AppTheme.label("", new Font("Segoe UI Emoji", Font.PLAIN, 30), AppTheme.TEXT);
        avatar.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel uname = AppTheme.label(user.getUsername(), AppTheme.HEADING_FONT, AppTheme.TEXT);
        uname.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel rating = AppTheme.label(String.format("%.1f", user.getAvgRating()), AppTheme.SMALL_FONT, AppTheme.WARNING);
        rating.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoCard.add(avatar);
        infoCard.add(Box.createVerticalStrut(4));
        infoCard.add(uname);
        infoCard.add(rating);
        sidebar.add(infoCard);
        sidebar.add(Box.createVerticalStrut(20));

        String[][] navItems = {{"", "Request Ride"}, {"", "My Rides"}, {"", "Rate Driver"}};
        for (String[] item : navItems) {
            JButton btn = navButton(item[0] + "  " + item[1]);
            sidebar.add(btn);
            sidebar.add(Box.createVerticalStrut(8));
            btn.addActionListener(e -> {
                switch (item[1]) {
                    case "Request Ride": showRequestRide(); break;
                    case "My Rides": showMyRides(); break;
                    case "Rate Driver": showRateDriver(); break;
                }
            });
        }

        sidebar.add(Box.createVerticalGlue());

        JButton logoutBtn = AppTheme.styledButton("Logout", new Color(127, 29, 29));
        logoutBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoutBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        logoutBtn.addActionListener(e -> {
            frame.setContentPane(new LoginPanel(system, frame));
            frame.revalidate(); frame.repaint();
        });
        sidebar.add(logoutBtn);

        return sidebar;
    }

    private JButton navButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(new Color(30, 41, 59));
        btn.setForeground(AppTheme.TEXT);
        btn.setFont(AppTheme.BODY_FONT);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        return btn;
    }

    private void showRequestRide() {
        contentArea.removeAll();
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(AppTheme.BG);
        panel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));

        JPanel card = AppTheme.card();
        card.setLayout(new GridBagLayout());
        card.setPreferredSize(new Dimension(480, 380));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 0, 8, 0);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0; gc.weightx = 1.0;

        gc.gridy = 0;
        card.add(AppTheme.label("Request a Ride", AppTheme.TITLE_FONT, AppTheme.TEXT), gc);

        gc.gridy = 1;
        card.add(AppTheme.label("Pickup Location", AppTheme.SMALL_FONT, AppTheme.SUBTEXT), gc);
        gc.gridy = 2; gc.insets = new Insets(2, 0, 12, 0);
        JTextField pickField = AppTheme.styledField();
        pickField.setPreferredSize(new Dimension(0, 40));
        card.add(pickField, gc);

        gc.gridy = 3; gc.insets = new Insets(8, 0, 2, 0);
        card.add(AppTheme.label("Drop Location", AppTheme.SMALL_FONT, AppTheme.SUBTEXT), gc);
        gc.gridy = 4; gc.insets = new Insets(2, 0, 12, 0);
        JTextField dropField = AppTheme.styledField();
        card.add(dropField, gc);

        gc.gridy = 5; gc.insets = new Insets(8, 0, 2, 0);
        card.add(AppTheme.label("Fare (Rs.)", AppTheme.SMALL_FONT, AppTheme.SUBTEXT), gc);
        gc.gridy = 6; gc.insets = new Insets(2, 0, 20, 0);
        JTextField fareField = AppTheme.styledField();
        card.add(fareField, gc);

        gc.gridy = 7; gc.insets = new Insets(8, 0, 8, 0);
        JButton reqBtn = AppTheme.styledButton("Request Ride", AppTheme.SUCCESS);
        reqBtn.setPreferredSize(new Dimension(0, 44));
        card.add(reqBtn, gc);

        gc.gridy = 8;
        JLabel msg = AppTheme.label("", AppTheme.SMALL_FONT, AppTheme.SUCCESS);
        msg.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(msg, gc);

        reqBtn.addActionListener(e -> {
            String pick = pickField.getText().trim();
            String drop = dropField.getText().trim();
            String fareStr = fareField.getText().trim();
            if (pick.isEmpty() || drop.isEmpty() || fareStr.isEmpty()) {
                msg.setForeground(new Color(239, 68, 68));
                msg.setText("Please fill all fields.");
                return;
            }
            try {
                double fare = Double.parseDouble(fareStr);
                // THIS NOW SAVES PERMANENTLY TO DB
                system.requestRide(pick, drop, fare, user.getUsername());
                msg.setForeground(AppTheme.SUCCESS);
                msg.setText("Ride saved to database successfully!");
                pickField.setText(""); dropField.setText(""); fareField.setText("");
            } catch (NumberFormatException ex) {
                msg.setForeground(new Color(239, 68, 68));
                msg.setText("Invalid fare amount.");
            }
        });

        panel.add(card);
        contentArea.add(panel, BorderLayout.CENTER);
        contentArea.revalidate();
        contentArea.repaint();
    }

    private void showMyRides() {
        contentArea.removeAll();
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setBackground(AppTheme.BG);
        panel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));

        panel.add(AppTheme.label("My Rides", AppTheme.TITLE_FONT, AppTheme.TEXT), BorderLayout.NORTH);

        String[] cols = {"ID", "Pickup", "Drop", "Fare", "Status", "Driver"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        // THIS NOW FETCHES LIVE DATA FROM DB
        for (Ride r : system.getAllRides()) {
            if (r.getUsername().equals(user.getUsername())) {
                model.addRow(new Object[]{r.getId(), r.getPick(), r.getDrop(), "Rs." + r.getFare(), r.getStatus(), r.getDriverName()});
            }
        }

        JTable table = buildTable(model);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(AppTheme.BG);
        scroll.getViewport().setBackground(AppTheme.CARD);
        scroll.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER));
        panel.add(scroll, BorderLayout.CENTER);

        contentArea.add(panel, BorderLayout.CENTER);
        contentArea.revalidate();
        contentArea.repaint();
    }

    private void showRateDriver() {
        contentArea.removeAll();
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setBackground(AppTheme.BG);
        panel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));

        panel.add(AppTheme.label("Rate Your Driver", AppTheme.TITLE_FONT, AppTheme.TEXT), BorderLayout.NORTH);

        JPanel ridesPanel = new JPanel();
        ridesPanel.setLayout(new BoxLayout(ridesPanel, BoxLayout.Y_AXIS));
        ridesPanel.setBackground(AppTheme.BG);

        boolean anyRide = false;
        for (Ride r : system.getAllRides()) {
            if (r.getUsername().equals(user.getUsername()) && r.getStatus().equals("Completed") && !r.isUserRated()) {
                anyRide = true;
                JPanel rideCard = AppTheme.card();
                rideCard.setLayout(new BorderLayout(10, 10));
                rideCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
                rideCard.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel info = AppTheme.label(r.getPick() + " to " + r.getDrop() + "  |  Driver: " + r.getDriverName(), AppTheme.BODY_FONT, AppTheme.TEXT);
                rideCard.add(info, BorderLayout.NORTH);

                JPanel ratingRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                ratingRow.setOpaque(false);
                JLabel starLabel = AppTheme.label("Rate (1-5):", AppTheme.SMALL_FONT, AppTheme.SUBTEXT);
                JComboBox<Integer> starBox = new JComboBox<>(new Integer[]{1, 2, 3, 4, 5});
                starBox.setBackground(new Color(30, 41, 59));
                starBox.setForeground(AppTheme.TEXT);
                JButton rateBtn = AppTheme.styledButton("Submit", AppTheme.ACCENT);
                JLabel feedback = AppTheme.label("", AppTheme.SMALL_FONT, AppTheme.SUCCESS);
                ratingRow.add(starLabel); ratingRow.add(starBox); ratingRow.add(rateBtn); ratingRow.add(feedback);
                rideCard.add(ratingRow, BorderLayout.CENTER);

                rateBtn.addActionListener(e -> {
                    int stars = (int) starBox.getSelectedItem();
                    r.setUserRated(true);
                    feedback.setText("Rated " + stars);
                    rateBtn.setEnabled(false);
                });

                ridesPanel.add(rideCard);
                ridesPanel.add(Box.createVerticalStrut(12));
            }
        }

        if (!anyRide) {
            JLabel noRides = AppTheme.label("No completed rides available to rate.", AppTheme.BODY_FONT, AppTheme.SUBTEXT);
            ridesPanel.add(noRides);
        }

        JScrollPane scroll = new JScrollPane(ridesPanel);
        scroll.setBackground(AppTheme.BG);
        scroll.getViewport().setBackground(AppTheme.BG);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        contentArea.add(panel, BorderLayout.CENTER);
        contentArea.revalidate();
        contentArea.repaint();
    }

    private JTable buildTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setBackground(AppTheme.CARD);
        table.setForeground(AppTheme.TEXT);
        table.setFont(AppTheme.BODY_FONT);
        table.setRowHeight(36);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(51, 65, 85));
        table.setSelectionForeground(AppTheme.TEXT);
        JTableHeader header = table.getTableHeader();
        header.setBackground(new Color(30, 41, 59));
        header.setForeground(AppTheme.SUBTEXT);
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER));
        return table;
    }
}

// ==========================================
// DRIVER DASHBOARD
// ==========================================
class DriverDashboard extends JPanel {
    private Driver driver;
    private RideSystem system;
    private JFrame frame;
    private JPanel contentArea;

    public DriverDashboard(Driver driver, RideSystem system, JFrame frame) {
        this.driver = driver;
        this.system = system;
        this.frame = frame;
        setLayout(new BorderLayout());
        setBackground(AppTheme.BG);

        add(buildSidebar(), BorderLayout.WEST);
        contentArea = new JPanel(new BorderLayout());
        contentArea.setBackground(AppTheme.BG);
        showAvailableRides();
        add(contentArea, BorderLayout.CENTER);
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(AppTheme.CARD);
        sidebar.setBorder(BorderFactory.createEmptyBorder(30, 20, 30, 20));
        sidebar.setPreferredSize(new Dimension(220, 0));

        JLabel logo = AppTheme.label("RideSwift DB", new Font("Segoe UI", Font.BOLD, 18), AppTheme.ACCENT2);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(logo);
        sidebar.add(Box.createVerticalStrut(30));

        JPanel infoCard = AppTheme.card();
        infoCard.setLayout(new BoxLayout(infoCard, BoxLayout.Y_AXIS));
        infoCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        JLabel avatar = AppTheme.label("", new Font("Segoe UI Emoji", Font.PLAIN, 30), AppTheme.TEXT);
        avatar.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel dname = AppTheme.label(driver.getUsername(), AppTheme.HEADING_FONT, AppTheme.TEXT);
        dname.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel rating = AppTheme.label(String.format("%.1f", driver.getAvgRating()), AppTheme.SMALL_FONT, AppTheme.WARNING);
        rating.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoCard.add(avatar);
        infoCard.add(Box.createVerticalStrut(4));
        infoCard.add(dname);
        infoCard.add(rating);
        sidebar.add(infoCard);
        sidebar.add(Box.createVerticalStrut(20));

        String[][] navItems = {{"", "Available Rides"}, {"", "Complete Ride"}, {"", "Rate User"}};
        for (String[] item : navItems) {
            JButton btn = navButton(item[0] + "  " + item[1]);
            sidebar.add(btn);
            sidebar.add(Box.createVerticalStrut(8));
            btn.addActionListener(e -> {
                switch (item[1]) {
                    case "Available Rides": showAvailableRides(); break;
                    case "Complete Ride": showCompleteRide(); break;
                    case "Rate User": showRateUser(); break;
                }
            });
        }

        sidebar.add(Box.createVerticalGlue());

        JButton logoutBtn = AppTheme.styledButton("Logout", new Color(127, 29, 29));
        logoutBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoutBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        logoutBtn.addActionListener(e -> {
            frame.setContentPane(new LoginPanel(system, frame));
            frame.revalidate(); frame.repaint();
        });
        sidebar.add(logoutBtn);

        return sidebar;
    }

    private JButton navButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(new Color(30, 41, 59));
        btn.setForeground(AppTheme.TEXT);
        btn.setFont(AppTheme.BODY_FONT);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        return btn;
    }

    private void showAvailableRides() {
        contentArea.removeAll();
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setBackground(AppTheme.BG);
        panel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        panel.add(AppTheme.label("Available Rides", AppTheme.TITLE_FONT, AppTheme.TEXT), BorderLayout.NORTH);

        JPanel ridesPanel = new JPanel();
        ridesPanel.setLayout(new BoxLayout(ridesPanel, BoxLayout.Y_AXIS));
        ridesPanel.setBackground(AppTheme.BG);

        boolean found = false;
        for (Ride r : system.getAllRides()) {
            if (r.getStatus().equals("Pending")) {
                found = true;
                JPanel card = AppTheme.card();
                card.setLayout(new BorderLayout(10, 8));
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
                card.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel info = AppTheme.label(r.getPick() + " to " + r.getDrop(), AppTheme.HEADING_FONT, AppTheme.TEXT);
                JLabel fareLabel = AppTheme.label("Rs." + r.getFare() + "  |  User: " + r.getUsername(), AppTheme.BODY_FONT, AppTheme.SUBTEXT);
                JPanel infoPanel = new JPanel();
                infoPanel.setOpaque(false);
                infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
                infoPanel.add(info);
                infoPanel.add(Box.createVerticalStrut(4));
                infoPanel.add(fareLabel);
                card.add(infoPanel, BorderLayout.CENTER);

                JButton acceptBtn = AppTheme.styledButton("Accept", AppTheme.SUCCESS);
                JLabel feedback = AppTheme.label("", AppTheme.SMALL_FONT, AppTheme.SUCCESS);
                JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                btnPanel.setOpaque(false);
                btnPanel.add(feedback); btnPanel.add(acceptBtn);
                card.add(btnPanel, BorderLayout.EAST);

                acceptBtn.addActionListener(e -> {
                    // --- NEW: Send 'Accepted' status to DB ---
                    system.updateRideStatusInDB(r.getId(), "Accepted", driver.getUsername());
                    
                    r.setStatus("Accepted");
                    r.setDriverName(driver.getUsername());
                    feedback.setText("Accepted in DB!");
                    acceptBtn.setEnabled(false);
                });

                ridesPanel.add(card);
                ridesPanel.add(Box.createVerticalStrut(12));
            }
        }

        if (!found) {
            ridesPanel.add(AppTheme.label("No pending rides at the moment.", AppTheme.BODY_FONT, AppTheme.SUBTEXT));
        }

        JScrollPane scroll = new JScrollPane(ridesPanel);
        scroll.setBackground(AppTheme.BG);
        scroll.getViewport().setBackground(AppTheme.BG);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        contentArea.add(panel, BorderLayout.CENTER);
        contentArea.revalidate();
        contentArea.repaint();
    }

    private void showCompleteRide() {
        contentArea.removeAll();
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setBackground(AppTheme.BG);
        panel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        panel.add(AppTheme.label("Complete Ride", AppTheme.TITLE_FONT, AppTheme.TEXT), BorderLayout.NORTH);

        JPanel ridesPanel = new JPanel();
        ridesPanel.setLayout(new BoxLayout(ridesPanel, BoxLayout.Y_AXIS));
        ridesPanel.setBackground(AppTheme.BG);

        boolean found = false;
        for (Ride r : system.getAllRides()) {
            if (r.getDriverName().equals(driver.getUsername()) && r.getStatus().equals("Accepted")) {
                found = true;
                JPanel card = AppTheme.card();
                card.setLayout(new BorderLayout(10, 8));
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
                card.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel info = AppTheme.label(r.getPick() + " to " + r.getDrop(), AppTheme.HEADING_FONT, AppTheme.TEXT);
                JLabel fareLabel = AppTheme.label("Rs." + r.getFare() + "  |  User: " + r.getUsername(), AppTheme.BODY_FONT, AppTheme.SUBTEXT);
                JPanel infoPanel = new JPanel();
                infoPanel.setOpaque(false);
                infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
                infoPanel.add(info);
                infoPanel.add(Box.createVerticalStrut(4));
                infoPanel.add(fareLabel);
                card.add(infoPanel, BorderLayout.CENTER);

                JButton completeBtn = AppTheme.styledButton("Complete", AppTheme.ACCENT2);
                JLabel feedback = AppTheme.label("", AppTheme.SMALL_FONT, AppTheme.SUCCESS);
                JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                btnPanel.setOpaque(false);
                btnPanel.add(feedback); btnPanel.add(completeBtn);
                card.add(btnPanel, BorderLayout.EAST);

                completeBtn.addActionListener(e -> {
                    // --- NEW: Send 'Completed' status to DB ---
                    system.updateRideStatusInDB(r.getId(), "Completed", driver.getUsername());
                    
                    r.setStatus("Completed");
                    feedback.setText("Completed in DB!");
                    completeBtn.setEnabled(false);
                });

                ridesPanel.add(card);
                ridesPanel.add(Box.createVerticalStrut(12));
            }
        }

        if (!found) {
            ridesPanel.add(AppTheme.label("No accepted rides to complete.", AppTheme.BODY_FONT, AppTheme.SUBTEXT));
        }

        JScrollPane scroll = new JScrollPane(ridesPanel);
        scroll.setBackground(AppTheme.BG);
        scroll.getViewport().setBackground(AppTheme.BG);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        contentArea.add(panel, BorderLayout.CENTER);
        contentArea.revalidate();
        contentArea.repaint();
    }

    private void showRateUser() {
        contentArea.removeAll();
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setBackground(AppTheme.BG);
        panel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        panel.add(AppTheme.label("Rate User", AppTheme.TITLE_FONT, AppTheme.TEXT), BorderLayout.NORTH);

        JPanel ridesPanel = new JPanel();
        ridesPanel.setLayout(new BoxLayout(ridesPanel, BoxLayout.Y_AXIS));
        ridesPanel.setBackground(AppTheme.BG);

        boolean found = false;
        for (Ride r : system.getAllRides()) {
            if (r.getDriverName().equals(driver.getUsername()) && r.getStatus().equals("Completed") && !r.isDriverRated()) {
                found = true;
                JPanel card = AppTheme.card();
                card.setLayout(new BorderLayout(10, 10));
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
                card.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel info = AppTheme.label(r.getPick() + " to " + r.getDrop() + "  |  User: " + r.getUsername(), AppTheme.BODY_FONT, AppTheme.TEXT);
                card.add(info, BorderLayout.NORTH);

                JPanel ratingRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                ratingRow.setOpaque(false);
                JComboBox<Integer> starBox = new JComboBox<>(new Integer[]{1, 2, 3, 4, 5});
                starBox.setBackground(new Color(30, 41, 59));
                starBox.setForeground(AppTheme.TEXT);
                JButton rateBtn = AppTheme.styledButton("Submit", AppTheme.ACCENT2);
                JLabel feedback = AppTheme.label("", AppTheme.SMALL_FONT, AppTheme.SUCCESS);
                ratingRow.add(AppTheme.label("Rate (1-5):", AppTheme.SMALL_FONT, AppTheme.SUBTEXT));
                ratingRow.add(starBox); ratingRow.add(rateBtn); ratingRow.add(feedback);
                card.add(ratingRow, BorderLayout.CENTER);

                rateBtn.addActionListener(e -> {
                    int stars = (int) starBox.getSelectedItem();
                    r.setDriverRated(true);
                    feedback.setText("Rated " + stars);
                    rateBtn.setEnabled(false);
                });

                ridesPanel.add(card);
                ridesPanel.add(Box.createVerticalStrut(12));
            }
        }

        if (!found) {
            ridesPanel.add(AppTheme.label("No completed rides available to rate.", AppTheme.BODY_FONT, AppTheme.SUBTEXT));
        }

        JScrollPane scroll = new JScrollPane(ridesPanel);
        scroll.setBackground(AppTheme.BG);
        scroll.getViewport().setBackground(AppTheme.BG);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        contentArea.add(panel, BorderLayout.CENTER);
        contentArea.revalidate();
        contentArea.repaint();
    }
}

// ==========================================
// MAIN ENTRY POINT
// ==========================================
public class RidingApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            RideSystem system = new RideSystem();
            JFrame frame = new JFrame("RideSwift DB");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(960, 680);
            frame.setLocationRelativeTo(null);
            frame.setMinimumSize(new Dimension(800, 560));
            frame.setContentPane(new LoginPanel(system, frame));
            frame.setVisible(true);
        });
    }
}