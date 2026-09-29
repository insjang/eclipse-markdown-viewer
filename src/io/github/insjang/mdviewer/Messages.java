package io.github.insjang.mdviewer;

import org.eclipse.osgi.util.NLS;

/** UI text; English in messages.properties, translations in messages_<lang>.properties. */
final class Messages extends NLS {
	public static String toolbar_pdf, toolbar_fonts;
	public static String pdf_title, pdf_job, pdf_failed, pdf_saved, pdf_error;
	public static String prefs_description, prefs_bodyFont, prefs_codeFont, prefs_fontSample, prefs_codeSample, prefs_lineHeight,
			prefs_theme, prefs_themeDark, prefs_themeLight, prefs_breaks, prefs_openPreview, prefs_darkColors, prefs_lightColors,
			prefs_spacing, prefs_nodeSpacing, prefs_rankSpacing, prefs_padding;
	public static String color_bg, color_fg, color_heading, color_strong, color_em, color_link, color_code, color_code_bg,
			color_pre_bg, color_quote, color_border, color_th_bg, color_row_alt;

	static {
		NLS.initializeMessages("io.github.insjang.mdviewer.messages", Messages.class); //$NON-NLS-1$
	}

	private Messages() {
	}
}
