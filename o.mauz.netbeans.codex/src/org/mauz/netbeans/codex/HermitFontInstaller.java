package org.mauz.netbeans.codex;

import java.util.Enumeration;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import org.openide.modules.OnStart;

/**
 * Replaces Swing UI defaults with Hermit while preserving the original style
 * and size for each key.
 */
@OnStart
public final class HermitFontInstaller implements Runnable {

    private static final String FONT_FAMILY = "Hermit";
    private static final String TOOLBAR_FONT_FAMILY = "Hermit Light";
    private static final int TOOLBAR_FONT_SIZE = 14;

    @Override
    public void run() {
        SwingUtilities.invokeLater(HermitFontInstaller::installHermitFonts);
    }

    private static void installHermitFonts() {
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource font && !FONT_FAMILY.equals(font.getFamily())) {
                UIManager.put(key, new FontUIResource(FONT_FAMILY, font.getStyle(), font.getSize()));
            }
        }
        UIManager.put("ToolBar.font", new FontUIResource(TOOLBAR_FONT_FAMILY, java.awt.Font.PLAIN, TOOLBAR_FONT_SIZE));
    }
}
