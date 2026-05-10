# RideSwift DB

RideSwift DB is a Java-based ride booking management system developed as an Object-Oriented Programming final project. The application uses Java Swing for the graphical user interface and MySQL for permanent database storage.

The system provides separate login options for users and drivers. Users can request rides, view their rides, and rate drivers. Drivers can view available rides, accept rides, complete rides, and rate users.

---

## Project Demo Video

https://youtu.be/o_nqRoJwvvs

---

## Features

- User login system
- Driver login system
- Separate dashboards for users and drivers
- Ride request feature
- View user ride history
- View available rides for drivers
- Accept ride feature
- Complete ride feature
- Rate driver feature
- Rate user feature
- MySQL database connectivity using JDBC
- Java Swing graphical user interface
- Custom exception handling for invalid login

---

## Technologies Used

- Java
- Java Swing
- MySQL
- JDBC
- Object-Oriented Programming

---

## OOP Concepts Used

### 1. Encapsulation

The project uses encapsulation by keeping class variables private and accessing them through getter and setter methods.

Examples:

- The `Account` class stores username, password, average rating, total points, and rating count as private data.
- The `Ride` class stores ride details such as ride ID, pickup location, drop location, fare, status, user, and driver as private data.

### 2. Abstraction

The project uses abstraction through the abstract `Account` class.

The `Account` class contains common account details and declares an abstract method:

```java
public abstract JPanel buildDashboard(RideSystem system, JFrame frame);
```

This method is implemented differently by the `User` and `Driver` classes.

### 3. Inheritance

The `User` and `Driver` classes inherit from the `Account` class.

```java
class User extends Account
class Driver extends Account
```

This allows both users and drivers to share common account properties while still having their own dashboard behavior.

### 4. Polymorphism

Polymorphism is used when the `buildDashboard()` method is overridden in both the `User` and `Driver` classes.

- A `User` object opens the user dashboard.
- A `Driver` object opens the driver dashboard.

### 5. Exception Handling

The project includes a custom exception class named `InvalidLoginException`.

This exception is used when incorrect login credentials are entered.

---

## Database Information

This project uses a MySQL database named:

```sql
ridebookingsystem
```

The database stores information about:

- Users
- Drivers
- Rides
- Pickup locations
- Drop locations
- Ride statuses

The database export file should be included in this project folder:

```text
database/ridebookingsystem.sql
```

---

## Database Setup Instructions

1. Open MySQL.
2. Create a new database named:

```sql
CREATE DATABASE ridebookingsystem;
```

3. Import the database file from:

```text
database/ridebookingsystem.sql
```

4. Make sure the database contains the required tables, such as:

```text
users
drivers
rides
pickup_locations
drop_locations
ride_status
```

---

## How to Run the Project

### Step 1: Install Required Software

Make sure the following software is installed:

- Java JDK
- MySQL Server
- MySQL Connector/J JDBC Driver
- A Java IDE such as IntelliJ IDEA, Eclipse, NetBeans, or VS Code

### Step 2: Open the Project

Open the project folder in your Java IDE.

### Step 3: Add MySQL JDBC Driver

Add the MySQL Connector/J `.jar` file to the project libraries.

The JDBC driver is required so the Java program can connect to the MySQL database.

### Step 4: Update Database Connection Details

In the `RideSystem` class, update the database connection details:

```java
String url = "jdbc:mysql://localhost:3306/ridebookingsystem";
String user = "your_mysql_username";
String password = "your_mysql_password";
```

Example:

```java
String url = "jdbc:mysql://localhost:3306/ridebookingsystem";
String user = "root";
String password = "your_mysql_password";
```

Do not upload your real MySQL password publicly on GitHub.

### Step 5: Run the Application

Run the main Java file:

```text
RidingApp.java
```

The application window will open with the RideSwift DB login screen.

---

## Main Classes

### RidingApp

This is the main class of the project. It starts the application and opens the login window.

### RideSystem

This class handles the main business logic and database operations.

It includes methods for:

- User login
- Driver login
- Requesting rides
- Fetching all rides
- Updating ride status in the database

### Account

This is an abstract class that stores common account information for both users and drivers.

### User

This class represents a ride-booking user.

### Driver

This class represents a driver who can accept and complete rides.

### Ride

This class stores ride information such as pickup location, drop location, fare, status, user, and driver.

### LoginPanel

This class creates the login screen.

### UserDashboard

This class creates the user dashboard.

User dashboard features include:

- Request ride
- View my rides
- Rate driver

### DriverDashboard

This class creates the driver dashboard.

Driver dashboard features include:

- View available rides
- Complete ride
- Rate user

### AppTheme

This class stores the colors, fonts, and styles used in the application interface.

---

## Project Folder Structure

```text
RideSwift/
│
├── RidingApp.java
├── README.md
│
└── database/
    └── ridebookingsystem.sql
```

---

## Login Information

Use usernames and passwords that already exist in the MySQL database.

User login details should be stored in the `users` table.

Driver login details should be stored in the `drivers` table.

---

## Important Notes

- MySQL must be running before opening the application.
- The database must be imported before testing the project.
- The database name should be `ridebookingsystem`.
- The MySQL username and password in the Java file must match your local MySQL settings.
- The project uses JDBC to connect Java with MySQL.
- Do not upload your real database password publicly.

---

## Author

Name: Shahar Bano Laghari and Muhammad Naeem
Course: Object-Oriented Programming  
Project Name: RideSwift DB  
Submission Type: Final Project

