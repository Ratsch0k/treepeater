import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import java.awt.Insets;

public class EditTargetDialogContent extends JPanel {
    private final JTextField host;
    private final JSpinner port;
    private final JCheckBox https;
    private final JCheckBox sni;

    public EditTargetDialogContent(String host, int port, boolean https, boolean sni) {
        super(new GridBagLayout());
    
        this.host = new JTextField(host == null ? "" : host);
        this.host.setColumns(28);

        this.port = new JSpinner(new SpinnerNumberModel(port, 1, 65535, 1));

        this.https = new JCheckBox("Use HTTPS", https);
        this.sni = new JCheckBox("Enable SNI", sni);
        this.sni.setEnabled(https);

        this.https.addActionListener(e -> {
            boolean on = this.https.isSelected();
            this.sni.setEnabled(on);
            if (!on) {
                this.sni.setSelected(false);
            } else if (this.host.getText() != null && !this.host.getText().trim().isEmpty()) {
                // sensible default when switching to HTTPS
                this.sni.setSelected(true);
            }
        });

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;
        gbc.gridx = 0;
        gbc.gridy = 0;

        this.add(new JLabel("Host"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        this.add(this.host, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0;
        this.add(new JLabel("Port"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        this.add(this.port, gbc);

        gbc.gridy++;
        gbc.gridx = 1;
        gbc.weightx = 1;
        this.add(this.https, gbc);

        gbc.gridy++;
        gbc.gridx = 1;
        gbc.weightx = 1;
        this.add(this.sni, gbc);

    }

    public TargetSettings getSettings() {
        return new TargetSettings(host.getText(), (Integer)port.getValue(), https.isSelected(), sni.isSelected());
    }
}
