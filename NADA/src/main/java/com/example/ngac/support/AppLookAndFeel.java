package com.example.ngac.support;

import javax.swing.UIManager;

/**
 * Applies a consistent, more polished look and feel (Nimbus - bundled with the JDK, no download
 * required) to NADA's window. Falls back silently to the platform default look and feel
 * if Nimbus is not available for any reason.
 */
public final class AppLookAndFeel {

    private AppLookAndFeel() {
    }

    public static void apply() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    return;
                }
            }
        } catch (Exception ignored) {
            // Keep whatever look and feel Swing already defaulted to.
        }
    }
}
