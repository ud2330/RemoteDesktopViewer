import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RemoteDesktopServer {
    private static final int PORT = 5000;
    private static final String AUTH_PASSWORD = "admin123";
    private static PrintWriter logWriter;
    private static JTextArea serverChatArea;
    private static JTextField serverChatInput;
    private static Set<ClientHandler> connectedClients = new HashSet<>();

    public static void main(String[] args) throws Exception {
        logWriter = new PrintWriter(new FileWriter("server_log.txt", true), true);

        JFrame serverFrame = new JFrame("Remote Desktop Server");
        serverFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        serverFrame.setSize(600, 400);
        serverFrame.setLayout(new BorderLayout());

        serverChatArea = new JTextArea();
        serverChatArea.setEditable(false);
        JScrollPane chatScrollPane = new JScrollPane(serverChatArea);
        chatScrollPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        serverFrame.add(chatScrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout());
        serverChatInput = new JTextField();
        inputPanel.add(serverChatInput, BorderLayout.CENTER);
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessageToAllClients(serverChatInput.getText()));
        inputPanel.add(sendButton, BorderLayout.EAST);
        inputPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        serverFrame.add(inputPanel, BorderLayout.SOUTH);

        serverFrame.setVisible(true);

        ExecutorService executorService = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            log("Server started on port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                log("Client connected: " + socket.getInetAddress().getHostAddress());
                ClientHandler clientHandler = new ClientHandler(socket);
                connectedClients.add(clientHandler);
                executorService.submit(clientHandler);
            }
        } catch (IOException e) {
            log("Server error: " + e.getMessage());
        } finally {
            executorService.shutdown();
            log("Server shutdown.");
        }
    }

    private static void sendMessageToAllClients(String message) {
        if (!message.trim().isEmpty()) {
            for (ClientHandler client : connectedClients) {
                client.sendMessage("Server: " + message);
            }
            serverChatArea.append("Server (You): " + message + "\n");
            serverChatInput.setText("");
            log("Sent chat message to all clients: " + message);
        }
    }

    private static void log(String msg) {
        String timestamped = new Date() + ": " + msg;
        System.out.println(timestamped);
        logWriter.println(timestamped);
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private ObjectInputStream ois;
        private ObjectOutputStream oos;
        private Robot robot;
        private Rectangle screenRect;
        private boolean streamingScreen = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void sendMessage(String message) {
            try {
                oos.writeObject("CHAT_MESSAGE");
                oos.writeObject(message);
            } catch (IOException e) {
                log("Error sending message to client " + socket.getInetAddress() + ": " + e.getMessage());
               
            }
        }

        @Override
        public void run() {
            try {
                oos = new ObjectOutputStream(socket.getOutputStream());
                ois = new ObjectInputStream(socket.getInputStream());

                
                String password = (String) ois.readObject();
                if (!AUTH_PASSWORD.equals(password)) {
                    oos.writeObject("AUTH_FAILED");
                    log("Authentication failed from " + socket.getInetAddress());
                    closeConnection();
                    return;
                }

                oos.writeObject("AUTH_SUCCESS");
                log("Authentication successful from " + socket.getInetAddress());
                serverChatArea.append("Client authenticated: " + socket.getInetAddress() + "\n");

                try {
                    robot = new Robot();
                    screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                } catch (AWTException e) {
                    log("Error creating Robot instance for " + socket.getInetAddress() + ": " + e.getMessage());
                    sendMessage("Error: Remote control features are not available on this server.");
                    closeConnection();
                    return;
                }

                while (socket.isConnected()) {
                    Object request = ois.readObject();

                    if (request instanceof String) {
                        String command = (String) request;

                        switch (command) {
                            case "START_SCREEN_STREAM":
                                streamingScreen = true;
                                startScreenStreaming();
                                break;
                            case "STOP_SCREEN_STREAM":
                                streamingScreen = false;
                                break;
                            case "GET_SCREEN":
                                sendScreen();
                                break;
                            case "CHAT_MESSAGE":
                                String msg = (String) ois.readObject();
                                serverChatArea.append("Client " + socket.getInetAddress() + ": " + msg + "\n");
                                log("Chat from " + socket.getInetAddress() + ": " + msg);
                                break;
                            case "MOUSE_MOVE":
                                int x = (int) ois.readObject();
                                int y = (int) ois.readObject();
                                if (robot != null) robot.mouseMove(x, y);
                                log("Mouse moved to: " + x + "," + y + " by " + socket.getInetAddress());
                                break;
                            case "MOUSE_CLICK":
                                int button = (int) ois.readObject();
                                int mask = InputEvent.getMaskForButton(button);
                                if (robot != null) {
                                    robot.mousePress(mask);
                                    robot.mouseRelease(mask);
                                }
                                log("Mouse click (Button " + button + ") by " + socket.getInetAddress());
                                break;
                            case "KEY_PRESS":
                                int key = (int) ois.readObject();
                                if (robot != null) {
                                    robot.keyPress(key);
                                    robot.keyRelease(key);
                                }
                                log("Key press: " + KeyEvent.getKeyText(key) + " by " + socket.getInetAddress());
                                break;
                            case "FILE_TRANSFER":
                                String filename = (String) ois.readObject();
                                byte[] fileData = (byte[]) ois.readObject();
                                try (FileOutputStream fos = new FileOutputStream("Received_" + filename)) {
                                    fos.write(fileData);
                                }
                                oos.writeObject("FILE_RECEIVED");
                                log("File received: " + filename + " from " + socket.getInetAddress());
                                break;
                            case "DISCONNECT":
                                log("Client disconnected: " + socket.getInetAddress());
                                serverChatArea.append("Client disconnected: " + socket.getInetAddress() + "\n");
                                closeConnection();
                                return;
                        }
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                log("Client error from " + socket.getInetAddress() + ": " + e.getMessage());
                serverChatArea.append("Client " + socket.getInetAddress() + " connection error.\n");
            } finally {
                closeConnection();
            }
        }

        private void startScreenStreaming() {
            new Thread(() -> {
                try {
                    while (socket.isConnected() && streamingScreen && robot != null) {
                        sendScreen();
                        Thread.sleep(100); 
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }

        private void sendScreen() {
            if (robot != null) {
                try {
                    BufferedImage screen = robot.createScreenCapture(screenRect);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(screen, "jpg", baos);
                    oos.writeObject("SCREEN_DATA");
                    oos.writeObject(baos.toByteArray());
                } catch (IOException e) {
                    log("Error sending screen to " + socket.getInetAddress() + ": " + e.getMessage());
                    closeConnection();
                }
            }
        }

        private void closeConnection() {
            connectedClients.remove(this);
            if (oos != null) {
                try {
                    oos.writeObject("SERVER_CLOSED"); 
                    oos.close();
                } catch (IOException e) {
                    
                }
            }
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                    
                }
            }
            if (socket != null && socket.isConnected()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    
                }
            }
            log("Connection with " + socket.getInetAddress() + " closed.");
        }
    }
}