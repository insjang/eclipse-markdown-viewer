package io.github.insjang.mdviewer;

import org.eclipse.osgi.util.NLS;

/** UI text; English in messages.properties, translations in messages_<lang>.properties. */
final class Messages extends NLS {
	public static String toolbar_pdf, toolbar_html, toolbar_copyHtml, toolbar_fonts, toolbar_showIn;
	public static String menu_copy, menu_copySource, menu_selectAll, menu_showIn, showIn_notWorkspace;
	public static String html_title, html_failed;
	public static String preview_frontMatter, preview_copy, preview_copied, preview_zoomReset, find_find, find_none, find_previous, find_next, find_close;
	public static String paste_failed;
	public static String link_job, link_noFile, link_noAnchor, link_noAnchorHere;
	public static String pdf_title, pdf_job, pdf_failed, pdf_saved, pdf_error;
	public static String prefs_description, prefs_bodyFont, prefs_codeFont, prefs_fontSample, prefs_codeSample, prefs_lineHeight,
			prefs_theme, prefs_themeAuto, prefs_themeDark, prefs_themeLight, prefs_breaks, prefs_openPreview, prefs_linkCheck, prefs_tabClose, prefs_darkColors, prefs_lightColors,
			prefs_spacing, prefs_nodeSpacing, prefs_rankSpacing, prefs_padding;
	public static String color_bg, color_fg, color_heading, color_strong, color_em, color_link, color_code, color_code_bg,
			color_pre_bg, color_quote, color_border, color_th_bg, color_row_alt;

	static {
		NLS.initializeMessages("io.github.insjang.mdviewer.messages", Messages.class); //$NON-NLS-1$
	}

	private Messages() {
	}
}
