// RemoteDesktopClient.java
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import javax.imageio.ImageIO;
import java.nio.file.Files;

public class RemoteDesktopClient extends JFrame implements MouseListener, MouseMotionListener, KeyListener {
    private static ObjectOutputStream oos;
    private static ObjectInputStream ois;
    private static Socket socket;
    private static JLabel screenLabel;
    private static JTextArea chatArea;
    private static JTextField chatInput;
    private boolean receivingScreen = false; // Flag to control screen updates

    public RemoteDesktopClient() {
        setTitle("Remote Desktop Client");
        setSize(1000, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        screenLabel = new JLabel();
        screenLabel.addMouseListener(this);
        screenLabel.addMouseMotionListener(this);
        add(new JScrollPane(screenLabel), BorderLayout.CENTER);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatInput = new JTextField();
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendChat());

        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);
        chatPanel.setPreferredSize(new Dimension(250, 0));
        add(chatPanel, BorderLayout.EAST);

        JButton connectButton = new JButton("Connect");
        JButton screenshotButton = new JButton("Get Screen"); // Changed button text
        JButton fileButton = new JButton("Send File");

        JPanel bottomPanel = new JPanel();
        bottomPanel.add(connectButton);
        bottomPanel.add(screenshotButton);
        bottomPanel.add(fileButton);
        add(bottomPanel, BorderLayout.SOUTH);

        connectButton.addActionListener(e -> connect());
        screenshotButton.addActionListener(e -> toggleScreenUpdates()); // Changed action
        fileButton.addActionListener(e -> sendFile());

        addKeyListener(this);
        setFocusable(true);
        requestFocusInWindow();
        setVisible(true);
    }

    public static void main(String[] args) {
        new RemoteDesktopClient();
    }

    private void connect() {
        try {
            String serverIP = JOptionPane.showInputDialog("Enter Server IP:");
            String password = JOptionPane.showInputDialog("Enter Password:");

            socket = new Socket(serverIP, 5000);
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());

            oos.writeObject(password);
            String reply = (String) ois.readObject();

            if ("AUTH_SUCCESS".equals(reply)) {
                JOptionPane.showMessageDialog(this, "Connected!");
                new Thread(this::receiveResponses).start();
            } else {
                JOptionPane.showMessageDialog(this, "Authentication Failed!");
                closeConnection();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Connection Error: " + e.getMessage());
            closeConnection();
        }
    }

    private void toggleScreenUpdates() {
        receivingScreen = !receivingScreen;
        try {
            oos.writeObject(receivingScreen ? "START_SCREEN_STREAM" : "STOP_SCREEN_STREAM");
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (receivingScreen) {
            setTitle("Remote Desktop Client - Streaming");
        } else {
            setTitle("Remote Desktop Client");
            screenLabel.setIcon(null); // Clear the screen when stopping
        }
    }

    private void getScreen() { // This method is no longer directly used by a button
        try {
            oos.writeObject("GET_SCREEN");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendChat() {
        try {
            String msg = chatInput.getText().trim();
            if (!msg.isEmpty()) {
                oos.writeObject("CHAT_MESSAGE");
                oos.writeObject(msg);
                chatArea.append("You: " + msg + "\n");
                chatInput.setText("");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendFile() {
        try {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                byte[] data = Files.readAllBytes(file.toPath());
                oos.writeObject("FILE_TRANSFER");
                oos.writeObject(file.getName());
                oos.writeObject(data);
                chatArea.append("File sent: " + file.getName() + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void receiveResponses() {
        try {
            while (true) {
                Object response = ois.readObject();
                if (response instanceof String) {
                    switch ((String) response) {
                        case "SCREEN_DATA":
                            if (receivingScreen) {
                                byte[] imageBytes = (byte[]) ois.readObject();
                                BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
                                ImageIcon icon = new ImageIcon(img);
                                screenLabel.setIcon(icon);
                                screenLabel.revalidate();
                                screenLabel.repaint();
                            } else {
                                // Consume the image data if not receiving screen
                                ois.readObject();
                            }
                            break;

                        case "CHAT_MESSAGE":
                            String chatMsg = (String) ois.readObject();
                            chatArea.append(chatMsg + "\n");
                            break;

                        case "FILE_RECEIVED":
                            chatArea.append("Server confirmed file received.\n");
                            break;

                        case "SERVER_CLOSED":
                            chatArea.append("Server has closed the connection.\n");
                            closeConnection();
                            return;
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            chatArea.append("Disconnected from server: " + e.getMessage() + "\n");
            e.printStackTrace();
            closeConnection();
        }
    }

    private void closeConnection() {
        receivingScreen = false;
        if (oos != null) {
            try {
                oos.writeObject("DISCONNECT");
                oos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (ois != null) {
            try {
                ois.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (socket != null && socket.isConnected()) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        oos = null;
        ois = null;
        socket = null;
        setTitle("Remote Desktop Client - Disconnected");
        screenLabel.setIcon(null);
    }

    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mousePressed(MouseEvent e) {
        if (socket != null && socket.isConnected()) {
            try {
                oos.writeObject("MOUSE_CLICK");
                oos.writeObject(e.getButton());
            } catch (IOException ex) {
                ex.printStackTrace();
                closeConnection();
            }
        }
    }
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}

    @Override public void mouseDragged(MouseEvent e) {}
    @Override public void mouseMoved(MouseEvent e) {
        if (socket != null && socket.isConnected()) {
            try {
                oos.writeObject("MOUSE_MOVE");
                oos.writeObject(e.getX());
                oos.writeObject(e.getY());
            } catch (IOException ex) {
                ex.printStackTrace();
                closeConnection();
            }
        }
    }

    @Override public void keyTyped(KeyEvent e) {}
    @Override public void keyPressed(KeyEvent e) {
        if (socket != null && socket.isConnected()) {
            try {
                oos.writeObject("KEY_PRESS");
                oos.writeObject(e.getKeyCode());
            } catch (IOException ex) {
                ex.printStackTrace();
                closeConnection();
            }
        }
    }
    @Override public void keyReleased(KeyEvent e) {}
}