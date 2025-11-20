# Remote Desktop Viewer

A Java-based Remote Desktop Viewer application that enables secure screen sharing and real-time remote control between a client and a server. This project includes remote desktop streaming, keyboard and mouse event control, real-time chat, file transfer, authentication, and session logging. A lightweight web interface is also provided using JSP and Servlets.

## Features

### 1. Real-Time Screen Sharing

* Streams the server’s screen to the client at regular intervals
* Efficient compression and image buffering for smoother performance
* Multi-threaded screen capture and transmission

### 2. Remote Control

* Client can control server mouse movements and clicks
* Full remote keyboard event forwarding
* AWT Robot used for executing input events on the server machine

### 3. Chat System

* Real-time two-way chat between client and server
* Uses socket-based messaging

### 4. Authentication

* Password-based authentication before connection
* Prevents unauthorized access
* Credentials validated on server side

### 5. Session Logging

* Logs timestamps, connection events, user actions
* Useful for audits and debugging

### 6. Web Interface (JSP + Servlets)

* Simple browser-based dashboard
* Allows accessing logs or viewing active connections
* Java backend connected via servlet controllers

### 7. Technology Stack

* Java
* Java Swing
* AWT (Robot, Toolkit)
* Socket Programming
* Multithreading
* JSP and Servlets

---

## Installation

### 1. Clone the Repository

```
git clone https://github.com/your-username/RemoteDesktopViewer.git
```

### 2. Navigate to the Project

```
cd RemoteDesktopViewer
```

### 3. Compile the Server

```
javac server/*.java
```

### 4. Compile the Client

```
javac client/*.java
```

---

## Running the Application

### Start the Server

```
java server.RemoteServer
```

The server will start listening for client connections, screen capture, and input event requests.

### Start the Client

```
java client.RemoteClient
```

Enter the server IP and authentication password when prompted.

---


---

## Requirements

* Java JDK 8 or later
* Stable LAN/Internet connection
* Apache Tomcat (optional for JSP interface)

---


