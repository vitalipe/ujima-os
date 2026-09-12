/* ujima-chooser: a GTK 3 module that hides the places sidebar of every file chooser.
 *
 * The in-process GtkFileChooserDialog (GIMP, Inkscape, Geany, LibreOffice, ONLYOFFICE, Qt apps
 * under the gtk3 theme, chromium without a portal) cannot be replaced, so it is aligned to the
 * file model instead: with the sidebar gone the dialog is the crumb trail + the folder view, and
 * home IS the Places screen (ujima.desktop.home). Recent, Trash, Other Locations, bookmarks and
 * the cwd row all lived in that panel.
 *
 * Hook the SIDEBAR's own map, not the chooser's: GtkPlacesSidebar calls gtk_widget_show_all on
 * itself at the end of every update_places (a bookmark change, a mount, the cwd shortcut), so a
 * one-time hide on the chooser does not stick. Loaded system-wide via gtk-modules in
 * /etc/gtk-3.0/settings.ini; inert in any process that never maps a chooser; if the .so is
 * missing GTK warns and the stock sidebar shows (fail-open). */
#include <gtk/gtk.h>

static gboolean
on_map (GSignalInvocationHint *hint, guint n_params, const GValue *params, gpointer data)
{
  GtkWidget *widget = g_value_get_object (params);

  if (GTK_IS_PLACES_SIDEBAR (widget) &&
      gtk_widget_get_ancestor (widget, GTK_TYPE_FILE_CHOOSER_WIDGET) != NULL)
    gtk_widget_hide (widget);

  return TRUE;   /* keep the hook */
}

G_MODULE_EXPORT void
gtk_module_init (gint *argc, gchar ***argv)
{
  /* module init runs before any widget exists: class_init is what registers the signals */
  g_type_class_ref (GTK_TYPE_WIDGET);
  g_signal_add_emission_hook (g_signal_lookup ("map", GTK_TYPE_WIDGET), 0, on_map, NULL, NULL);
}
