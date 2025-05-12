package su.qwa.altlauncher;

import javax.swing.*;
import javax.xml.parsers.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.*;
import org.w3c.dom.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.ArrayList;

public class Altlauncher extends JFrame {
	private static final String VERSION = "1.2";
    private static final String BUCKET_URL = "https://vedro.qwa.su/AltbetaRuntime/";
    private static final String SESSION_URL = "https://altbeta.qwa.su/getsession.php";
    private static final File ROOT = getRootPath();

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JLabel statusLabel;

    public Altlauncher() {
    	Settings settings = new Settings();
        setTitle("Altlauncher " + VERSION);
        setSize(400, 300);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        usernameField = new JTextField(16);
        passwordField = new JPasswordField(16);
        statusLabel = new JLabel(" ");
        
        usernameField.setText(settings.lastLogin);

        JButton launchButton = new JButton("Запуск");
        launchButton.addActionListener(e -> launch());
        JButton settingsButton = new JButton("Настройки");
        settingsButton.addActionListener(e -> SettingsGUI.run());

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.gridx = 0;
        constraints.gridy = 0;
        panel.add(new JLabel("Ник:"), constraints);
        constraints.gridx = 1;
        panel.add(usernameField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        panel.add(new JLabel("Пароль:"), constraints);
        constraints.gridx = 1;
        panel.add(passwordField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        panel.add(statusLabel, constraints);
        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 1;
        panel.add(settingsButton, constraints);
        constraints.gridx = 1;
        panel.add(launchButton, constraints);
        add(panel);
    }
    
    private static void runCLI() {
        try {
            Settings settings = new Settings();
            Console console = System.console();
            String username;
            char[] passwordChars;

            // Ввод логина и пароля
            if (console != null) {
                username = console.readLine("Ник: ");
                passwordChars = console.readPassword("Пароль: ");
            } else {
                System.out.print("Ник: ");
                username = new Scanner(System.in).nextLine();
                System.out.print("Пароль: ");
                passwordChars = new Scanner(System.in).nextLine().toCharArray();
            }
            String password = new String(passwordChars);
            Arrays.fill(passwordChars, ' ');

            // Сохраняем логин
            settings.lastLogin = username;
            settings.save();

            // Процесс запуска
            System.out.println("Авторизация...");
            String session = Authenticator.getSession(username, password);
            
            System.out.println("Загрузка файлов...");
            Downloader.downloadAll();
            
            System.out.println("Запуск клиента...");
            Launcher.run(session, username);
            
            System.out.println("Клиент запущен.");
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void launch() {
        Settings settings = new Settings();
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());
        settings.lastLogin = username;
        settings.save();

        new Thread(() -> {
            try {
                status("Авторизация...");
                String session = Authenticator.getSession(username, password);
                status("Загрузка файлов...");
                Downloader.downloadAll();
                status("Запуск клиента...");
                Launcher.run(session, username);
                status("Клиент запущен.");
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, e, "Ошибка: " + e.getMessage(), JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }

    private void status(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
    }

    private static File getRootPath() {
        String os = System.getProperty("os.name").toLowerCase();
        String path = os.contains("win")
                ? System.getenv("APPDATA") + "\\.altbeta\\runtime"
                : System.getProperty("user.home") + "/.altbeta/runtime";
        File dir = new File(path);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("nogui")) {
            runCLI();
        } else {
            SwingUtilities.invokeLater(() -> new Altlauncher().setVisible(true));
        }
    }

    // -------------------- Authenticator --------------------
    static class Authenticator {
        public static String getSession(String username, String password) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) new URL(SESSION_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.getOutputStream().write(("username=" + URLEncoder.encode(username, "UTF-8")
                    + "&password=" + URLEncoder.encode(password, "UTF-8")).getBytes());

            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                return in.readLine();
            }
        }
    }

    // -------------------- Downloader --------------------
    public static class Downloader {
        private static class FileInfo {
            private final String key;
            private final Instant lastModified;

            public FileInfo(String key, Instant  lastModified) {
                this.key = key;  
                this.lastModified = lastModified;
            }

            public String getKey() {
                return key;
            }

            public Instant getLastModified() {
                return lastModified;
            }
        }

        public static void downloadAll() throws Exception {
            List<FileInfo> files = new ArrayList<>();
            HttpURLConnection conn = (HttpURLConnection) new URL(BUCKET_URL).openConnection();
            try (InputStream in = conn.getInputStream()) {
                Document doc = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(in);
                NodeList contents = doc.getElementsByTagName("Contents");
                for (int i = 0; i < contents.getLength(); i++) {
                    Element el = (Element) contents.item(i);
                    String key = el.getElementsByTagName("Key")
                            .item(0)
                            .getTextContent()
                            .trim();
                    String lastModifiedStr = el.getElementsByTagName("LastModified")
                            .item(0)
                            .getTextContent()
                            .trim();
                    Instant lastModified = Instant.parse(lastModifiedStr);
                    files.add(new FileInfo(key, lastModified));
                }
            }

            for (FileInfo fileInfo : files) {
                String fileKey = fileInfo.getKey();
                Instant cloudLastModified = fileInfo.getLastModified();

                File outFile = new File(ROOT, fileKey.replace("/", File.separator));
                if (!outFile.getParentFile().exists()) {
                    outFile.getParentFile().mkdirs();
                }

                boolean shouldDownload = !outFile.exists();

                if (outFile.exists()) {
                    Instant localLastModified = Instant.ofEpochMilli(outFile.lastModified());
                    if (cloudLastModified.isAfter(localLastModified)) {
                        shouldDownload = true;
                    }
                }

                if (shouldDownload) {
                    URL url = new URL(BUCKET_URL + URLEncoder.encode(fileKey, "UTF-8"));
                    try (InputStream in = url.openStream();
                         FileOutputStream out = new FileOutputStream(outFile)) {
                        in.transferTo(out);
                    }
                    // Обновляем дату модификации файла
                    outFile.setLastModified(cloudLastModified.toEpochMilli());
                }
            }
        }
    }


    // -------------------- Launcher --------------------
    static class Launcher {
        public static void run(String session, String username) throws IOException {
            Settings settings = new Settings();

            List<String> libs = new ArrayList<>();
            File[] all = ROOT.listFiles();
            for (File file : all) {
                if (file.getName().endsWith(".jar") && !file.getName().equals("deobfuscated.jar"))
                    libs.add(file.getAbsolutePath());
            }

            String classpath = String.join(File.pathSeparator, libs) + File.pathSeparator +
                    new File(ROOT, "deobfuscated.jar").getAbsolutePath();

            List<String> cmd = new ArrayList<>();
            cmd.add(settings.javaPath);
            cmd.add("-Xms" + settings.xms);
            cmd.add("-Xmx" + settings.xmx);
            if (settings.useG1Gc) cmd.add("-XX:+UseG1GC");
            cmd.add("-Djava.library.path=" + new File(ROOT, "natives").getAbsolutePath());
            cmd.add("-cp");
            cmd.add(classpath);
            cmd.add("net.minecraft.client.Minecraft");
            cmd.add(username);
            cmd.add(session);
            
            System.out.println(cmd);

            new ProcessBuilder(cmd)
                    .directory(ROOT)
                    .inheritIO()
                    .start();
        }
    }
    
    // -------------------- Settings --------------------
    static class Settings {
        public String javaPath = "java";
        public String xms = "1024m";
        public String xmx = "1024m";
        public boolean useG1Gc = true;
        public String lastLogin = "";

        public Settings() {
            File file = new File(ROOT, "settings.txt");
            if (!file.exists()) return;
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    String[] kv = line.split("=");
                    if (kv.length != 2) continue;
                    switch (kv[0].trim()) {
	                    case "javaPath":
	                        javaPath = kv[1].trim();
	                        break;
	                    case "xms":
	                        xms = kv[1].trim();
	                        break;
	                    case "xmx":
	                        xmx = kv[1].trim();
	                        break;
	                    case "useG1Gc":
	                        useG1Gc = Boolean.parseBoolean(kv[1].trim());
	                        break;
	                    case "lastLogin":
	                    	lastLogin = kv[1].trim();
	                        break;
	                    // возможно, default:
                    }
                }
            } catch (IOException ignored) {}
        }
        
        public void save() {
            File file = new File(ROOT, "settings.txt");
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println("javaPath=" + javaPath);
                writer.println("xms=" + xms);
                writer.println("xmx=" + xmx);
                writer.println("useG1Gc=" + useG1Gc);
                writer.println("lastLogin=" + lastLogin);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    // -------------------- SettingsGUI --------------------
    static class SettingsGUI extends JFrame {
        private JTextField javaPathField;
        private JTextField xmsField;
        private JTextField xmxField;
        private JCheckBox g1gcCheckBox;

        public SettingsGUI() {
            setTitle("Настройки");
            setSize(400, 250);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setResizable(false);

            Settings settings = new Settings();

            javaPathField = new JTextField(settings.javaPath, 16);
            xmsField = new JTextField(settings.xms, 16);
            xmxField = new JTextField(settings.xmx, 16);
            g1gcCheckBox = new JCheckBox("Использовать G1GC", settings.useG1Gc);

            JButton saveButton = new JButton("Сохранить");
            saveButton.addActionListener(e -> saveSettings());

            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.WEST;

            gbc.gridx = 0; gbc.gridy = 0;
            panel.add(new JLabel("Путь к Java:"), gbc);
            gbc.gridx = 1;
            panel.add(javaPathField, gbc);

            gbc.gridx = 0; gbc.gridy = 1;
            panel.add(new JLabel("Xms (минимум памяти):"), gbc);
            gbc.gridx = 1;
            panel.add(xmsField, gbc);

            gbc.gridx = 0; gbc.gridy = 2;
            panel.add(new JLabel("Xmx (максимум памяти):"), gbc);
            gbc.gridx = 1;
            panel.add(xmxField, gbc);

            gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
            panel.add(g1gcCheckBox, gbc);

            gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
            gbc.anchor = GridBagConstraints.CENTER;
            panel.add(saveButton, gbc);

            add(panel);
        }

        private void saveSettings() {
        	Settings currentSettings = new Settings();
        	currentSettings.javaPath = javaPathField.getText().trim();
            currentSettings.xms = xmsField.getText().trim();
            currentSettings.xmx = xmxField.getText().trim();
            currentSettings.useG1Gc = g1gcCheckBox.isSelected();

            File file = new File(ROOT, "settings.txt");

            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            	currentSettings.save();
                JOptionPane.showMessageDialog(this, "Настройки сохранены.", "Успех", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Ошибка сохранения настроек: " + e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }

        public static void run() {
            SwingUtilities.invokeLater(() -> new SettingsGUI().setVisible(true));
        }
    }

}
