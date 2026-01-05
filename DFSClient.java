import java.io.*;
import java.net.*;
import java.util.*;

public class DFSClient {
    private String currentFilePath;
    private String localCache;
    private String accessMode; // "READ", "WRITE", "RW"
    private boolean isDirty = false; // 変更があったか
    private String serverHost;
    private int serverPort = 8080;

    public static void main(String[] args) {
        DFSClient client = new DFSClient();
        try {
            //System.out.println("Testing DFSClient...");
            //client.open("localhost", "sample.txt", "RW");
            // Read current content
            //System.out.println("Current cache: " + client.read());
            // Rewrite conte

            //System.out.println("Test completed");

            
            // Open file with read-write mode(rw)
            Scanner scanner = new Scanner(System.in);
            
            
            while (true) {
                System.out.print("> ");
                String command = scanner.nextLine().trim();
                String[] parts = command.split(" ", 4);
                
                if (parts.length == 0) continue;
                String cmd = parts[0].toLowerCase();
                switch (cmd) {
                    case "open":
                        if (parts.length >= 4) {
                            client.open(parts[1], parts[2], parts[3]);
                        } else {
                        System.out.println("Usage: open <host> <path> <mode>");
                        }
                        break;

                    case "read":
                        System.out.println("Current cache: " + client.read());
                        break;
                    case "write":
                        if (parts.length >= 2) {
                            client.write(parts[1]);
                            //System.out.println("Data written.");
                        } else {
                            System.out.println("Usage: write <content>");
                        }
                        break;
                
                    case "close":
                        client.close();
                        break;

                    case "-ls":
                        client.listFiles();
                        break;

                    case "view_locks":
                        client.viewLocks();
                        break;

                    case "exit":
                        System.out.println("Test completed");
                        return;
                    default:
                        System.out.println("Unknown command. Available: open, read, write, close, exit");
            }
        }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void listFiles() throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("-ls");
            String line;
            System.out.println("Files on server:");
            while (!(line = in.readLine()).equals("FILES_LIST_END")) {
                if (!line.equals("FILES_LIST_START")) {
                    System.out.println(" - " + line);
                }
            }
        }
    }

    public void viewLocks() throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("VIEW_LOCKS");
            String line;
            System.out.println("Current Locks on server:");
            while (!(line = in.readLine()).equals("LOCKS_LIST_END")) {
                if (!line.equals("LOCKS_LIST_START")) {
                    System.out.println(" - " + line);
                }
            }
        }
    }

    // open: サーバからデータを取得しキャッシュする
    public void open(String siteName, String path, String mode) throws IOException {
        this.serverHost = siteName;
        this.currentFilePath = path;
        this.accessMode = mode;

        try (Socket socket = new Socket(serverHost, serverPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("FETCH " + path + " " + mode);
            String status = in.readLine();
            if ("SUCCESS".equals(status)) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                this.localCache = sb.toString().trim();
                this.isDirty = false;
                System.out.println("Opened: " + path + " [" + mode + "]");
            } else {
                throw new IOException("Server Error: " + status);
            }
        }
    }

    public String read() {
        if (accessMode.equals("WRITE_ONLY"))
            return "Error: Write only";
        return localCache;
    }

    public void write(String newContent) {
        if (accessMode.equals("READ_ONLY")) {
            System.out.println("Error: Read only mode");
            return;
        }
        this.localCache = newContent;
        this.isDirty = true;
    }

    // close: 変更があればサーバへ送る
    public void close() throws IOException {
        if ((accessMode.contains("WRITE") || accessMode.contains("RW"))) {
            try (Socket socket = new Socket(serverHost, serverPort);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                if(isDirty){
                    out.println("STORE " + currentFilePath);
                    out.print(localCache + "\n__END__\n");
                    out.flush();

                    if ("SUCCESS".equals(in.readLine())) {
                        System.out.println("Changes saved to server.");
                    }
                }else{
                    out.println("UNLOCK " + currentFilePath);
                    if ("SUCCESS".equals(in.readLine())) System.out.println("No changes to save.");
                }
        
            }
        }

        this.localCache = null;
        this.currentFilePath = null;
        System.out.println("Client closed.");
    }
}
