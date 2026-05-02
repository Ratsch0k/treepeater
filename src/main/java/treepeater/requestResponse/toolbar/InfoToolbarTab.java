package treepeater.requestResponse.toolbar;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import treepeater.components.RoundedPanel;
import treepeater.icons.InfoIcon;
import treepeater.requestResponse.RequestResponseChangeListener;

/**
 * Expand-panel tab summarizing the HTTP target, request line, and the current response metadata.
 */
public class InfoToolbarTab implements RequestResponseChangeListener {
    private static final DateTimeFormatter RECEIVED_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ToolbarIconButton button;
    private final JPanel content;
    private RoundedPanel requestCard;
    private RoundedPanel responseCard;

    private final JLabel methodValue = valueLabel();
    private final JLabel protocolValue = valueLabel();
    private final JLabel hostValue = valueLabel();
    private final JLabel portValue = valueLabel();
    private final JLabel urlValue = valueLabel();
    private final JLabel pathValue = valueLabel();
    private final JLabel statusValue = valueLabel();
    private final JLabel sizeValue = valueLabel();
    private final JLabel receivedValue = valueLabel();

    public InfoToolbarTab() {
        this.button = new ToolbarIconButton(new InfoIcon());
        this.content = new JPanel(new BorderLayout());
        this.content.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        this.content.add(this.buildContent(), BorderLayout.CENTER);
    }

    public void updateUI() {
        this.applyThemeLocalStyles();
    }

    public void applyThemeLocalStyles() {
        this.button.applyLocalTheme();

        if (this.requestCard != null) {
            InfoToolbarTab.applyInfoCardTheme(this.requestCard);
        }
        if (this.responseCard != null) {
            InfoToolbarTab.applyInfoCardTheme(this.responseCard);
        }
    }

    public JButton getButton() {
        return this.button;
    }

    public JPanel getContent() {
        return this.content;
    }

    public void applyLocalTheme() {
        this.button.applyLocalTheme();
    }

    @Override
    public void onRequestChanged(HttpRequest request, HttpResponse response, LocalDateTime responseReceivedAt) {
        this.updateDisplay(request, response, responseReceivedAt);
    }

    @Override
    public void onResponseChanged(HttpRequest request, HttpResponse response, LocalDateTime responseReceivedAt) {
        this.updateDisplay(request, response, responseReceivedAt);
    }

    /**
     * Updates labels from the current request/response and optional receive time for the active history entry.
     */
    private void updateDisplay(HttpRequest request, HttpResponse response, LocalDateTime responseReceivedAt) {
        if (request != null) {
            HttpService service = safe(() -> request.httpService(), null);
            if (service != null) {
                this.protocolValue.setText(service.secure() ? "HTTPS" : "HTTP");
                String host = service.host();
                this.hostValue.setText((host == null || host.isBlank()) ? emDash() : host);
                int port = service.port();
                this.portValue.setText(port > 0 ? String.valueOf(port) : emDash());
            } else {
                this.protocolValue.setText(emDash());
                this.hostValue.setText(emDash());
                this.portValue.setText(emDash());
            }
            this.methodValue.setText(safe(() -> request.method(), emDash()));
            this.urlValue.setText(safe(() -> request.url(), emDash()));
            this.pathValue.setText(safe(() -> request.path(), emDash()));
        } else {
            this.methodValue.setText(emDash());
            this.protocolValue.setText(emDash());
            this.hostValue.setText(emDash());
            this.portValue.setText(emDash());
            this.urlValue.setText(emDash());
            this.pathValue.setText(emDash());
        }

        if (response != null) {
            short code = response.statusCode();
            String reason = safe(() -> response.reasonPhrase(), "");
            String statusText = reason == null || reason.isBlank() ? String.valueOf(code) : code + " " + reason;
            this.statusValue.setText(statusText);

            int bodyLen = byteArrayLength(response.body());
            int totalLen = byteArrayLength(response.toByteArray());
            if (totalLen == bodyLen) {
                this.sizeValue.setText(formatBytes(bodyLen) + " (body)");
            } else {
                this.sizeValue.setText(formatBytes(bodyLen) + " body, " + formatBytes(totalLen) + " total");
            }
        } else {
            this.statusValue.setText(emDash());
            this.sizeValue.setText(emDash());
        }

        if (responseReceivedAt != null) {
            this.receivedValue.setText(responseReceivedAt.format(RECEIVED_FMT));
        } else {
            this.receivedValue.setText(emDash());
        }
    }

    /** Clears the summary when no request tab is selected. */
    public void clearDisplay() {
        this.updateDisplay(null, null, null);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout());
        JPanel heading = new ToolbarTabTitle("Info");

        JPanel contentPanel = new JPanel();
        contentPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        contentPanel.setLayout(new GridBagLayout());

        GridBagConstraints contentContraints = new GridBagConstraints();
        contentContraints.anchor = GridBagConstraints.FIRST_LINE_START;
        contentContraints.insets = new Insets(0, 0, 16, 0);
        contentContraints.gridx = 0;
        contentContraints.gridy = 0;
        contentContraints.weightx = 1;
        contentContraints.weighty = 0;
        contentContraints.fill = GridBagConstraints.HORIZONTAL;

        this.buildRequestContent();
        contentPanel.add(this.requestCard, contentContraints);

        contentContraints.gridy = 1;
        this.buildResponseContent();
        contentPanel.add(this.responseCard, contentContraints);

        // Absorb extra vertical space below the forms so the grid stays top-aligned.
        GridBagConstraints glue = new GridBagConstraints();
        glue.gridx = 0;
        glue.gridy = 2;
        glue.weightx = 1;
        glue.weighty = 1;
        glue.fill = GridBagConstraints.BOTH;
        glue.anchor = GridBagConstraints.FIRST_LINE_START;
        glue.insets = new Insets(0, 0, 0, 0);
        contentPanel.add(Box.createVerticalGlue(), glue);

        root.add(heading, BorderLayout.NORTH);
        root.add(contentPanel, BorderLayout.CENTER);
        return root;
    }

    private void buildRequestContent() {
        this.requestCard = InfoToolbarTab.createInfoCard();

        this.requestCard.setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.insets = new Insets(0, 0, 6, 0);
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;

        int row = 0;
        c.gridy = row++;
        this.requestCard.add(sectionLabel("Target"), c);
        this.kvRow(++row, this.requestCard, "Method", this.methodValue);
        this.kvRow(++row, this.requestCard, "Protocol", this.protocolValue);
        this.kvRow(++row, this.requestCard, "Host", this.hostValue);
        this.kvRow(++row, this.requestCard, "Port", this.portValue);
        this.kvRow(++row, this.requestCard, "URL", this.urlValue);
        this.kvRow(++row, this.requestCard, "Path", this.pathValue);
    }

    private void buildResponseContent() {
        this.responseCard = InfoToolbarTab.createInfoCard();
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(0, 0, 6, 0);
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;

        int row = 0;

        c.gridy = row++;
        this.responseCard.add(sectionLabel("Response"), c);
        c.insets = new Insets(0, 0, 6, 0);
        this.kvRow(++row, this.responseCard, "Status", this.statusValue);
        this.kvRow(++row, this.responseCard, "Size", this.sizeValue);
        this.kvRow(++row, this.responseCard, "Received", this.receivedValue);
    }

    private static RoundedPanel createInfoCard() {
        RoundedPanel card = new RoundedPanel();
        card.setLayout(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        card.setOpaque(false);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        InfoToolbarTab.applyInfoCardTheme(card);
        return card;
    }

    private static void applyInfoCardTheme(RoundedPanel card) {
        card.setBackgroundColor(UIManager.getColor("Colors.ui.background.3"));
        card.setBorderColor(UIManager.getColor("Colors.ui.background.3"));
    }

    private static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD).deriveFont(l.getFont().getSize2D() + 2f));
        return l;
    }

    private JPanel kvRow(int row, RoundedPanel form, String key, JLabel valueLabel) {
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.insets = new Insets(0, 0, 6, 4);
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;

        JLabel keyLabel = new JLabel(key + ":");
        keyLabel.setFont(keyLabel.getFont().deriveFont(Font.BOLD));

        form.add(keyLabel, c);

        c.gridx = 1;
        c.weightx = 1;
        form.add(valueLabel, c);
        return form;
    }

    private static JLabel valueLabel() {
        JLabel l = new JLabel();
        l.setText(emDash());
        l.setHorizontalAlignment(SwingConstants.LEFT);
        return l;
    }

    private static String emDash() {
        return "—";
    }

    private static String formatBytes(int n) {
        return n + " B";
    }

    private static int byteArrayLength(ByteArray bytes) {
        if (bytes == null) {
            return 0;
        }
        return bytes.length();
    }

    private static <T> T safe(Supplier<T> supplier, T onFailure) {
        try {
            return supplier.get();
        } catch (Exception ignored) {
            return onFailure;
        }
    }

    private static String safe(Supplier<String> supplier, String onFailure) {
        try {
            String s = supplier.get();
            return (s == null || s.isBlank()) ? onFailure : s;
        } catch (Exception ignored) {
            return onFailure;
        }
    }
}
