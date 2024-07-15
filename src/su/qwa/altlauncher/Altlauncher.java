package su.qwa.altlauncher;

import javax.swing.*;
import java.awt.*;
//import java.awt.event.ActionEvent;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;


public class Altlauncher extends JFrame {
	private static final long serialVersionUID = 6993467786750939603L;
	private JTextField nicknameField;
    private JPasswordField passwordField;
    private JCheckBox rememberMeCheckBox;
    private JLabel downloadLabel;
    private final String APPDATA = System.getenv("APPDATA") + "/.altbeta";
    private final String CLIENT_PATH = APPDATA + "/client";
    private final String NATIVES_PATH = CLIENT_PATH + "/bin/natives";
    private final String BIN_PATH = CLIENT_PATH + "/bin";
    private Settings settings;
    private final String Title = "Altlauncher rc-j0.3";
    private final int VersionId = 2;

    public Altlauncher() {
        setTitle(Title);
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        createDirectories();
        checkLauncherUpdate();
    }

    private void initComponents() {
        nicknameField = new JTextField(15);
        passwordField = new JPasswordField(15);
        rememberMeCheckBox = new JCheckBox("Запомнить меня?");

        JButton loginButton = new JButton("Начать игру");
        loginButton.addActionListener(e -> start());

        JButton settingsButton = new JButton("Настройки");
        settingsButton.addActionListener(e -> showSettingsDialog());

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(5, 5, 5, 5);

        constraints.gridx = 0;
        constraints.gridy = 0;
        panel.add(new JLabel("Никнейм:"), constraints);
        constraints.gridx = 1;
        panel.add(nicknameField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        panel.add(new JLabel("Пароль:"), constraints);
        constraints.gridx = 1;
        panel.add(passwordField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        panel.add(rememberMeCheckBox, constraints);
        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 1;
        panel.add(loginButton, constraints);
        constraints.gridx = 1;
        panel.add(settingsButton, constraints);

        downloadLabel = new JLabel("Загрузка файлов...");
        downloadLabel.setVisible(false);
        constraints.gridy = 6;
        panel.add(downloadLabel, constraints);

        add(panel);
    }

    private void start() {
        String nickname = nicknameField.getText();
        String password = new String(passwordField.getPassword());
        authenticate(nickname, password, 13);
    }

    private void authenticate(String user, String password, int version) {
        String url = "http://altbeta.qwa.su/game/auth.php";
        String data = "user=" + user + "&password=" + password + "&version=" + version;

        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            try (OutputStream os = con.getOutputStream()) {
                os.write(data.getBytes());
            }

            if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                    String responseText = in.readLine();
                    if ("Bad login".equals(responseText)) {
                        showError("Ошибка", "Неправильное имя пользователя или пароль");
                    } else {
                        String[] parts = responseText.split(":");
                        String username = parts[2];
                        String sessionId = parts[3];
                        if (rememberMeCheckBox.isSelected()) saveAutoLogin(user, password);
                        initializeMinecraft(username, sessionId);
                    }
                }
            } else {
                showError("Ошибка", String.valueOf(con.getResponseCode()));
            }
        } catch (IOException e) {
            showError("Ошибка", e.getMessage());
        }
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private void showInfo(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void initializeMinecraft(String username, String sessionId) {
        checkGameUpdate();
        String javaExecutable = settings.javaPath.isEmpty() ? System.getenv("JAVA_HOME") + "bin/javaw.exe" : settings.javaPath;
        String jars = String.join(";", getJarsList());
        String jvmArguments = String.format("-Xms%s -Xmx%s -Xmn%s", settings.xms, settings.xmx, settings.xmn);
        if (settings.useG1Gc) jvmArguments += " -XX:+UseG1GC";

        String command = String.format("%s %s -Xss1m -Djava.library.path=%s -cp \"%s\" net.minecraft.client.Minecraft %s %s",
            javaExecutable, jvmArguments, NATIVES_PATH, jars, username, sessionId);

        try {
            Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            showError("Ошибка", e.getMessage());
        }
    }

	private List<String> getJarsList() {
        return Arrays.asList(
    		BIN_PATH + "/altbeta.jar",
            BIN_PATH + "/asm-9.2.jar",
            BIN_PATH + "/asm-tree-9.2.jar",
            BIN_PATH + "/codecjorbis-20230120.jar",
            BIN_PATH + "/codecwav-20101023.jar",
            BIN_PATH + "/jinput-2.0.5.jar",
            BIN_PATH + "/jinput-platform-2.0.5-natives-windows.jar",
            BIN_PATH + "/json-20230311.jar",
            BIN_PATH + "/jutils-1.0.0.jar",
            BIN_PATH + "/launchwrapper-1.0.jar",
            BIN_PATH + "/libraryjavasound-20101123.jar",
            BIN_PATH + "/librarylwjglopenal-20100824.jar",
            BIN_PATH + "/lwjgl-2.9.4.jar",
            BIN_PATH + "/lwjgl-platform-2.9.3-natives-windows.jar",
            BIN_PATH + "/lwjgl_util-2.9.4.jar",
            BIN_PATH + "/rdi-1.0.jar",
            BIN_PATH + "/soundsystem-20120107.jar"
        );
    }

    private void showSettingsDialog() {
        JTextField javaPathField = new JTextField(settings.javaPath, 20);
        JTextField xmsField = new JTextField(settings.xms, 20);
        JTextField xmxField = new JTextField(settings.xmx, 20);
        JTextField xmnField = new JTextField(settings.xmn, 20);
        JCheckBox g1GcCheckBox = new JCheckBox("Использовать G1GC", settings.useG1Gc);

        JPanel panel = new JPanel(new GridLayout(6, 2));
        panel.add(new JLabel("Путь к виртуальной машине Java:"));
        panel.add(javaPathField);
        panel.add(new JLabel("Начальный размер памяти:"));
        panel.add(xmsField);
        panel.add(new JLabel("Максимальный размер памяти:"));
        panel.add(xmxField);
        panel.add(new JLabel("Размер области нового поколения:"));
        panel.add(xmnField);
        panel.add(new JLabel("Сборщик мусора G1GC:"));
        panel.add(g1GcCheckBox);

        if (JOptionPane.showConfirmDialog(this, panel, "Настройки", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            settings.javaPath = javaPathField.getText();
            settings.xms = xmsField.getText();
            settings.xmx = xmxField.getText();
            settings.xmn = xmnField.getText();
            settings.useG1Gc = g1GcCheckBox.isSelected();
            saveSettings();
        }
    }

    private void saveSettings() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(APPDATA + "/altlauncher/settings.txt"))) {
            writer.write(String.join("\n", settings.javaPath, settings.xms, settings.xmx, settings.xmn, Boolean.toString(settings.useG1Gc)));
        } catch (IOException e) {
            showError("Ошибка", e.getMessage());
        }
    }

    private void loadSettings() {
        File file = new File(APPDATA + "/altlauncher/settings.txt");
        if (!file.exists()) {
            settings = new Settings(System.getenv("JAVA_HOME") + "/bin/javaw.exe", "512m", "1g", "128m", false);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            settings = new Settings(reader.readLine(), reader.readLine(), reader.readLine(), reader.readLine(), Boolean.parseBoolean(reader.readLine()));
        } catch (IOException e) {
            showError("Ошибка", e.getMessage());
        }
    }

    private void saveAutoLogin(String username, String password) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(APPDATA + "/altlauncher/autologin.txt"))) {
            writer.write(String.join("\n", username, password));
        } catch (IOException e) {
            showError("Ошибка", e.getMessage());
        }
    }

    private void loadAutoLogin() {
        File file = new File(APPDATA + "/altlauncher/autologin.txt");
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            nicknameField.setText(reader.readLine());
            passwordField.setText(reader.readLine());
            rememberMeCheckBox.setSelected(true);
        } catch (IOException e) {
            showError("Ошибка", e.getMessage());
        }
    }

    private void createDirectories() {
        new File(CLIENT_PATH).mkdirs();
    }

    private void checkGameUpdate() {
        downloadLabel.setVisible(true);

        try {
            List<String> localFiles = getFileList(new File(BIN_PATH));
            List<String> serverFiles = getFileListFromServer("http://a0914225.xsph.ru/altbeta-files/");

            for (String file : serverFiles) {
                if (!localFiles.contains(file)) {
                    if (file.endsWith("/")) {
                        createDirectory(BIN_PATH, file);
                    } else {
                        downloadFile(file, BIN_PATH);
                    }
                }
            }
        } catch (Exception e) {
            showError("Ошибка", e.getMessage());
        }

        downloadLabel.setVisible(false);
    }

    private void createDirectory(String basePath, String dir) {
        File directory = new File(basePath + File.separator + dir);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }


    private List<String> getFileList(File dir) {
        List<String> fileList = new ArrayList<>();
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) fileList.add(file.getName());
                }
            }
        }
        return fileList;
    }

    private List<String> getFileListFromServer(String url) throws Exception {
        List<String> fileList = new ArrayList<>();

        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("GET");
        if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(con.getInputStream());
            NodeList nodes = doc.getElementsByTagName("Key");
            for (int i = 0; i < nodes.getLength(); i++) {
                fileList.add(nodes.item(i).getTextContent());
            }
        }
        return fileList;
    }
    
    // спасеба chatgpt потому что я ленивая ж
    private void downloadFile(String filePath, String basePath) {
        try {
            URL url = new URL("http://a0914225.xsph.ru/altbeta-files/" + filePath);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("HEAD");
            long serverLastModified = con.getLastModified();

            File outFile = new File(basePath + File.separator + filePath);
            if (outFile.exists()) {
                long localLastModified = outFile.lastModified();
                if (localLastModified >= serverLastModified) {
                    // Файл уже обновлен, скачивать не нужно
                    return;
                }
            }

            // Скачивание файла, если он отсутствует или старый
            InputStream in = url.openStream();
            outFile.getParentFile().mkdirs();
            Files.copy(in, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            in.close();
            
            // Установить дату последнего изменения
            if (serverLastModified > 0) {
                outFile.setLastModified(serverLastModified);
            }
        } catch (IOException e) {
            showError("Ошибка загрузки файла", e.getMessage());
        }
    }



    private void checkLauncherUpdate() {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL("http://a0914225.xsph.ru/altbeta-launcherVersions/index.xml").openConnection();
            con.setRequestMethod("GET");
            if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document doc = builder.parse(con.getInputStream());
                int latestVersion = Integer.parseInt(doc.getElementsByTagName("id").item(0).getTextContent());

                if (VersionId < latestVersion) {
//                    if (JOptionPane.showConfirmDialog(this, "Доступна новая версия лаунчера. Обновить?", "Обновление", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
//                        downloadLauncherUpdate();
//                        showInfo("Информация", "Перезапустите лаунчер для применения обновления.");
//                        System.exit(0);
//                    }
                	showInfo("Информация", "Доступно обновление лаунчера, пожалуйста скачайте его с сайта");
                }
            }
        } catch (Exception e) {
            showError("Ошибка", e.getMessage());
        }
    }

    private void downloadLauncherUpdate() throws IOException {
        String url = "http://altbeta.qwa.su/game/launcher.jar";
        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, Paths.get(APPDATA, "altlauncher.jar"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Altlauncher launcher = new Altlauncher();
            launcher.setVisible(true);
            launcher.loadSettings();
            launcher.loadAutoLogin();
        });
    }

    static class Settings {
        String javaPath;
        String xms;
        String xmx;
        String xmn;
        boolean useG1Gc;

        Settings(String javaPath, String xms, String xmx, String xmn, boolean useG1Gc) {
            this.javaPath = javaPath;
            this.xms = xms;
            this.xmx = xmx;
            this.xmn = xmn;
            this.useG1Gc = useG1Gc;
        }
    }
}
