/*
 * This is free software, licensed under the Gnu Public License (GPL)
 * get a copy from <http://www.gnu.org/licenses/gpl.html>
 */
package henplus.view;


/**
 * <p>Title: ExtendedColumn</p>
 * <p>Description:<br>
 * Created on: 25.07.2003</p>
 *
 * @author <a href="mailto:martin.grotzke@javakaffee.de">Martin Grotzke</a>
 * @version $Id: ExtendedColumn.java,v 1.4 2004-03-07 14:22:03 hzeller Exp $
 */
public final class ExtendedColumn extends Column
{

  public static final int ALIGN_LEFT = ColumnMetaData.ALIGN_LEFT;
  public static final int ALIGN_CENTER = ColumnMetaData.ALIGN_CENTER;
  public static final int ALIGN_RIGHT = ColumnMetaData.ALIGN_RIGHT;

  private final int _colspan;
  private final int _alignment;
  private boolean _outputBold;

  public ExtendedColumn(int value, int alignment)
  {
    super(value);
    _colspan = 1;
    _alignment = alignment;
  }

  public ExtendedColumn(String text, int alignment)
  {
    super(text);
    _colspan = 1;
    _alignment = alignment;
  }

  public ExtendedColumn(int value, int colspan, int alignment)
  {
    super(value);
    _colspan = colspan;
    _alignment = alignment;
  }

  public ExtendedColumn(String text, int colspan, int alignment)
  {
    super(text);
    _colspan = colspan;
    _alignment = alignment;
  }

  public int getColspan()
  {
    return _colspan;
  }

  public int getAlignment()
  {
    return _alignment;
  }

  /**
   * Call this to test if there's a special output mode set.
   */
  public boolean isBoldRequested()
  {
    return _outputBold;
  }

  public void setBoldRequested(boolean bold)
  {
    _outputBold = bold;
  }
}
