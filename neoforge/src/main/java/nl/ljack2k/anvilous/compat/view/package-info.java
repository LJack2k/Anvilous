/**
 * What a recipe viewer should show, with no viewer in it.
 *
 * <p>Two plugins draw the same three categories — JEI's in {@code compat.jei}, EMI's in
 * {@code compat.emi} — and neither mod is a dependency, so nothing here may touch either
 * one's classes. Only the arithmetic of "this tool, this price, this result" lives here;
 * slots, layouts and registration stay with the viewer that owns them.
 */
package nl.ljack2k.anvilous.compat.view;
