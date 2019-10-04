package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.naming.NamingException;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.*;
import static nl.opengeogroep.safetymaps.server.db.DB.qr;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.json.JSONObject;

/**
 *
 * @author matthijsln
 */
@StrictBinding
@UrlBinding("/admin/action/linkify")
public class LinkifyActionBean implements ActionBean, ValidationErrorHandler {
    private static final String JSP = "/WEB-INF/jsp/admin/linkify.jsp";

    private ActionBeanContext context;

    private List<String> wordList;

    private Map<String,String> termByWord = new HashMap();

    private JSONObject settings;

    @Validate(required=true, mask = "[A-Za-z]+")
    private String word;

    @Validate
    private String term;

    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public List<String> getWordList() {
        return wordList;
    }

    public void setWordList(List<String> wordList) {
        this.wordList = wordList;
    }

    public Map<String, String> getTermByWord() {
        return termByWord;
    }

    public void setTermByWord(Map<String, String> termByWord) {
        this.termByWord = termByWord;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }
    // </editor-fold>

    @Before
    private void loadInfo() throws NamingException, SQLException {

        Object o = qr().query("select options from organisation.modules where name = 'incidents'", new ScalarHandler<>());
        settings = new JSONObject((o != null ? o.toString() : "{}"));

        JSONObject words = new JSONObject();
        if(settings.has("linkifyWords")) {
            words = settings.getJSONObject("linkifyWords");
        }

        // Key value is String for a search term, or any other type to search with
        // the original key value
        // { "some word": true, "other word": "search term" }
        wordList = new ArrayList(words.keySet());
        Collections.sort(wordList);
        for(String word: wordList) {
            if(words.get(word) instanceof String) {
                termByWord.put(word, words.getString(word));
            }
        }
    }

    @Override
    public Resolution handleValidationErrors(ValidationErrors errors) throws Exception {
        loadInfo();
        return list();
    }

    @DefaultHandler
    @DontValidate
    public Resolution list() throws Exception {
        return new ForwardResolution(JSP);
    }

    @DontValidate
    public Resolution edit() throws Exception {

        if(word != null) {
            Object o = settings.getJSONObject("linkifyWords").get(word);
            if(o instanceof String) {
                term = (String)o;
            }
        }

        return new ForwardResolution(JSP);
    }

    public Resolution save() throws Exception {

        word = word.toLowerCase();
        if(term != null) {
            settings.getJSONObject("linkifyWords").put(word, term);
        } else {
            settings.getJSONObject("linkifyWords").put(word, 1);
        }

        saveSettings();

        getContext().getMessages().add(new SimpleMessage("Woord opgeslagen."));
        return new RedirectResolution(this.getClass()).flash(this);
    }

    private void saveSettings() throws Exception {
        qr().update("update organisation.modules set options = ?::json where name = 'incidents'", settings.toString(4));
    }

    @DontValidate
    public Resolution delete() throws Exception {
        String word = context.getRequest().getParameter("word");

        settings.getJSONObject("linkifyWords").remove(word);
        saveSettings();

        getContext().getMessages().add(new SimpleMessage("Woord verwijderd."));
        return new RedirectResolution(this.getClass()).flash(this);
    }

    @DontValidate
    public Resolution cancel() {
        return new RedirectResolution(this.getClass()).flash(this);
    }
}
