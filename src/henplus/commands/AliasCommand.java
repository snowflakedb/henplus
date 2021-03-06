/*
 * This is free software, licensed under the Gnu Public License (GPL)
 * get a copy from <http://www.gnu.org/licenses/gpl.html>
 * $Id: AliasCommand.java,v 1.17 2005-11-27 16:20:27 hzeller Exp $
 * author: Henner Zeller <H.Zeller@acm.org>
 */
package henplus.commands;

import henplus.AbstractCommand;
import henplus.Command;
import henplus.CommandDispatcher;
import henplus.HenPlus;
import henplus.SQLSession;
import henplus.io.ConfigurationContainer;
import henplus.view.Column;
import henplus.view.ColumnMetaData;
import henplus.view.TableRenderer;
import henplus.view.util.SortedMatchIterator;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * A Command that handles Aliases.
 */
public final class AliasCommand extends AbstractCommand
{
  private final static String ALIAS_FILENAME = "aliases";
  private final static ColumnMetaData[] DRV_META;

  static
  {
    DRV_META = new ColumnMetaData[2];
    DRV_META[0] = new ColumnMetaData("alias");
    DRV_META[1] = new ColumnMetaData("execute command");
  }

  private final ConfigurationContainer _config;
  private final SortedMap/*<ClassName-String,Command-Class>*/ _aliases;
  private final CommandDispatcher _dispatcher;

  /**
   * to determine, if we got a recursion: one alias calls another
   * alias which in turn calls the first one ..
   */
  private final Set _currentExecutedAliases;

  /**
   * returns the command-strings this command can handle.
   */
  public String[] getCommandList()
  {
    return new String[]{
        "list-aliases", "alias", "unalias"
    };
  }

  public AliasCommand(HenPlus henplus)
  {
    _dispatcher = henplus.getDispatcher();
    _aliases = new TreeMap();
    _currentExecutedAliases = new HashSet();
    _config = henplus.createConfigurationContainer(ALIAS_FILENAME);
  }

  /**
   * initial load of aliases.
   */
  public void load()
  {
    Map props = _config.readProperties();
    Iterator it = props.entrySet().iterator();
    while (it.hasNext())
    {
      Map.Entry entry = (Map.Entry) it.next();
      putAlias((String) entry.getKey(),
               (String) entry.getValue());
    }
  }

  public boolean requiresValidSession(String cmd)
  {
    return false;
  }

  private void putAlias(String alias, String value)
  {
    _aliases.put(alias, value);
    _dispatcher.registerAdditionalCommand(alias, this);
  }

  private void removeAlias(String alias)
  {
    _aliases.remove(alias);
    _dispatcher.unregisterAdditionalCommand(alias);
  }

  /**
   * execute the command given.
   */
  public int execute(SQLSession currentSession, String cmd, String param)
  {
    StringTokenizer st = new StringTokenizer(param);
    int argc = st.countTokens();

    if ("list-aliases".equals(cmd))
    {
      if (argc != 0)
      {
        return SYNTAX_ERROR;
      }
      showAliases();
      return SUCCESS;
    }

    else if ("alias".equals(cmd))
    {
      if (argc < 2)
      {
        return SYNTAX_ERROR;
      }
      String alias = (String) st.nextElement();
      // no quoted aliases..
      if (alias.startsWith("\"") || alias.startsWith("'"))
      {
        return SYNTAX_ERROR;
      }
      // unless we override an alias, moan, if this command already
      // exists.
      if (!_aliases.containsKey(alias)
          && _dispatcher.containsCommand(alias))
      {
        HenPlus.msg().println("cannot alias built-in command!");
        return EXEC_FAILED;
      }
      param = param.trim();
      for (int i = 0; i < param.length(); ++i)
      {
        if (Character.isWhitespace(param.charAt(i)))
        {
          param = param.substring(i).trim();
          break;
        }
      }
      String value = stripQuotes(param); // rest of values.
      putAlias(alias, value);
    }

    else if ("unalias".equals(cmd))
    {
      if (argc >= 1)
      {
        while (st.hasMoreElements())
        {
          String alias = (String) st.nextElement();
          if (!_aliases.containsKey(alias))
          {
            HenPlus.msg().println("unknown alias '"
                                  + alias + "'");
          }
          else
          {
            removeAlias(alias);
          }
        }
        return SUCCESS;
      }
      return SYNTAX_ERROR;
    }

    else
    {
      String toExecute = (String) _aliases.get(cmd);
      //HenPlus.msg().println("key: '" + cmd + "' - exec: " + toExecute);
      if (toExecute == null)
      {
        return EXEC_FAILED;
      }
      // not session-proof:
      if (_currentExecutedAliases.contains(cmd))
      {
        HenPlus.msg().println("Recursive call to aliases ["
                              + cmd
                              + "]. Stopping this senseless venture.");
        _currentExecutedAliases.clear();
        return EXEC_FAILED;
      }
      HenPlus.msg().println("execute alias: " + toExecute + param);
      _currentExecutedAliases.add(cmd);
      _dispatcher.execute(currentSession, toExecute + param);
      _currentExecutedAliases.clear();
    }
    return SUCCESS;
  }

  private void showAliases()
  {
    DRV_META[0].resetWidth();
    DRV_META[1].resetWidth();
    TableRenderer table = new TableRenderer(DRV_META, HenPlus.out());
    Iterator it = _aliases.entrySet().iterator();
    while (it.hasNext())
    {
      Map.Entry entry = (Map.Entry) it.next();
      Column[] row = new Column[2];
      row[0] = new Column((String) entry.getKey());
      row[1] = new Column((String) entry.getValue());
      table.addRow(row);
    }
    table.closeTable();
  }

  private String stripQuotes(String value)
  {
    if (value.startsWith("\"") && value.endsWith("\""))
    {
      value = value.substring(1, value.length() - 1);
    }
    else if (value.startsWith("\'") && value.endsWith("\'"))
    {
      value = value.substring(1, value.length() - 1);
    }
    return value;
  }

  public Iterator complete(CommandDispatcher disp,
                           String partialCommand, final String lastWord)
  {
    StringTokenizer st = new StringTokenizer(partialCommand);
    String cmd = (String) st.nextElement();
    int argc = st.countTokens();

    // list-aliases gets no names.
    if ("list-aliases".equals(cmd))
    {
      return null;
    }

    /*
     * some completion within the alias/unalias commands.
     */
    if ("alias".equals(cmd) || "unalias".equals(cmd))
    {
      final HashSet alreadyGiven = new HashSet();

      if ("alias".equals(cmd))
      {
        // do not complete beyond first word.
        if (argc > ("".equals(lastWord) ? 0 : 1))
        {
          return null;
        }
      }
      else
      {
        /*
         * remember all aliases, that have already been given on
         * the commandline and exclude from completion..
         * cool, isn't it ?
         */
        while (st.hasMoreElements())
        {
          alreadyGiven.add(st.nextElement());
        }
      }

      // ok, now return the list.
      return new SortedMatchIterator(lastWord, _aliases)
      {
        protected boolean exclude(String current)
        {
          return alreadyGiven.contains(current);
        }
      };
    }

    /* ok, someone tries to complete something that is a command.
     * try to find the actual command and ask that command to do
     * the completion.
     */
    String toExecute = (String) _aliases.get(cmd);
    if (toExecute != null)
    {
      Command c = disp.getCommandFrom(toExecute);
      if (c != null)
      {
        int i = 0;
        String param = partialCommand;
        while (param.length() < i
               && Character.isWhitespace(param.charAt(i)))
        {
          ++i;
        }
        while (param.length() < i
               && !Character.isWhitespace(param.charAt(i)))
        {
          ++i;
        }
        return c.complete(disp, toExecute + param.substring(i),
                          lastWord);
      }
    }

    return null;
  }

  public void shutdown()
  {
    _config.storeProperties(_aliases, true, "Aliases...");
  }

  /**
   * return a descriptive string.
   */
  public String getShortDescription()
  {
    return "handle Aliases";
  }

  public String getSynopsis(String cmd)
  {
    if ("list-aliases".equals(cmd))
    {
      return cmd;
    }
    else if ("alias".equals(cmd))
    {
      return cmd + " <alias-name> <command-to-execute>";
    }
    else if ("unalias".equals(cmd))
    {
      return cmd + " <alias-name>";
    }
    else
    {
      /*
       * special aliased name..
       */
      return cmd;
    }
  }

  public String getLongDescription(String cmd)
  {
    String dsc = null;
    if ("list-aliases".equals(cmd))
    {
      dsc = "\tList all aliases, that have been stored with the\n"
            + "\t'alias' command";
    }
    else if ("alias".equals(cmd))
    {
      dsc = "\tAdd an alias for a command. This means, that you can\n"
            + "\tgive a short name for a command you often use.  This\n"
            + "\tmight be as simple as\n"
            + "\t   alias ls tables\n"
            + "\tto execute the tables command with a short 'ls'.\n"
            + "\n\tFor longer commands it is even more helpful:\n"
            + "\t   alias size select count(*) from\n"
            + "\tThis command  needs a table  name as a  parameter to\n"
            + "\texpand  to  a  complete command.  So 'size students'\n"
            + "\texpands to 'select count(*) from students' and yields\n"
            + "\tthe expected result.\n"
            + "\n\tTo make life easier, HenPlus tries to determine the\n"
            + "\tcommand  to be executed so that the  tab-completion\n"
            + "\tworks even here; in this latter case it  would help\n"
            + "\tcomplete table names.";
    }
    else if ("unalias".equals(cmd))
    {
      dsc = "\tremove an alias name";
    }
    else
    {
      // not session-proof:
      if (_currentExecutedAliases.contains(cmd))
      {
        dsc = "\t[ this command cyclicly references itself ]";
      }
      else
      {
        _currentExecutedAliases.add(cmd);
        dsc = "\tThis is an alias for the command\n"
              + "\t   " + _aliases.get(cmd);

        String actualCmdStr = (String) _aliases.get(cmd);
        if (actualCmdStr != null)
        {
          StringTokenizer st = new StringTokenizer(actualCmdStr);
          actualCmdStr = st.nextToken();
          Command c = _dispatcher.getCommandFrom(actualCmdStr);
          String longDesc = null;
          if (c != null
              && (longDesc = c.getLongDescription(actualCmdStr)) != null)
          {
            dsc += "\n\n\t..the following description could be determined for this";
            dsc += "\n\t------- [" + actualCmdStr + "] ---\n";
            dsc += longDesc;
          }
          _currentExecutedAliases.clear();
        }
      }
    }
    return dsc;
  }
}

/*
 * Local variables:
 * c-basic-offset: 4
 * compile-command: "ant -emacs -find build.xml"
 * End:
 */
