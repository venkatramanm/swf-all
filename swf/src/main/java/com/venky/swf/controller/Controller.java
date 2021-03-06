/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.venky.swf.controller;

import com.venky.core.log.SWFLogger;
import com.venky.core.log.TimerStatistics.Timer;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectHolder;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Registry;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.JdbcTypeHelper.TypeConverter;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.annotations.model.EXPORTABLE;
import com.venky.swf.db.jdbc.ConnectionManager;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.User;
import com.venky.swf.db.model.io.xls.XLSModelReader;
import com.venky.swf.db.model.io.xls.XLSModelWriter;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.db.table.BindVariable;
import com.venky.swf.db.table.Table;
import com.venky.swf.db.table.Table.ColumnDescriptor;
import com.venky.swf.exceptions.AccessDeniedException;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.lucene.index.LuceneIndexer;
import com.venky.swf.routing.Config;
import com.venky.swf.routing.Router;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.DashboardView;
import com.venky.swf.views.HtmlView;
import com.venky.swf.views.HtmlView.StatusType;
import com.venky.swf.views.RedirectorView;
import com.venky.swf.views.View;
import com.venky.swf.views.login.LoginView;
import com.venky.swf.views.model.FileUploadView;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.simple.JSONObject;

import javax.activation.MimetypesFileTypeMap;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 *
 * @author venky
 */
public class Controller {

    public static final int MAX_LIST_RECORDS = 30;
    protected Path path;

    public Path getPath() {
        return path;
    }

    public Controller(Path path) {
        this.path = path;
    }

    public View tron(String loggerName) {
        Config.instance().getLogger(StringUtil.valueOf(loggerName)).setLevel(Level.ALL);
        return back();
    }

    public View troff(String loggerName) {
        Config.instance().getLogger(StringUtil.valueOf(loggerName)).setLevel(Level.OFF);
        return back();
    }

    public View reset_router() {
        if (Config.instance().isDevelopmentEnvironment()) {
            Router.instance().reset();
        }
        return back();
    }

    @RequireLogin(false)
    public View login() {
        if (getPath().getRequest().getMethod().equals("GET") && getPath().getSession() == null) {
            if (getPath().getFormFields().isEmpty() || getPath().getFormFields().containsKey("_REGISTER")) {
                return createLoginView();
            } else {
                return authenticate();
            }
        } else if (getPath().getSession() != null) {
            if (getSessionUser() == null) {
                StringBuilder msg = new StringBuilder();
                getPath().getErrorMessages().forEach(m -> msg.append(m));
                if (msg.length() > 0){
                    if (getPath().getProtocol() == MimeType.TEXT_HTML){
                        return createLoginView(StatusType.ERROR,msg.toString());
                    }else {
                        throw new AccessDeniedException(msg.toString());
                    }
                }else {
                    if (getPath().getProtocol() == MimeType.TEXT_HTML){
                        return createLoginView();
                    }else {
                        return authenticate();
                    }
                }
            } else {
                if (getPath().getProtocol() == MimeType.TEXT_HTML){
                    return new RedirectorView(getPath(), loginSuccessful(), "");
                }else {
                    return authenticate();
                }
            }
        } else {
            return authenticate();
        }
    }

    protected String loginSuccessful() {
        String redirectedTo = getPath().getRequest().getParameter("_redirect_to");
        return loginSuccessful(redirectedTo == null ? "" : redirectedTo);
    }

    protected String loginSuccessful(String redirectedTo) {
        if (ObjectUtil.isVoid(redirectedTo)) {
            redirectedTo = "/dashboard";
        }
        return redirectedTo;
    }

    protected View authenticate() {
        boolean authenticated = getPath().isRequestAuthenticated();
        if (getPath().getProtocol() == MimeType.TEXT_HTML) {
            if (authenticated) {
                return new RedirectorView(getPath(), "", loginSuccessful());
            } else {
                StringBuilder msg = new StringBuilder();
                getPath().getErrorMessages().forEach(m -> msg.append(m));
                return createLoginView(StatusType.ERROR, msg.toString());
            }
        } else {
            IntegrationAdaptor<User, ?> adaptor = IntegrationAdaptor.instance(User.class, FormatHelper.getFormatClass(getPath().getProtocol()));
            if (adaptor == null) {
                throw new RuntimeException(" content-type in request header should be " + MimeType.APPLICATION_JSON + " or " + MimeType.APPLICATION_XML);
            }
            if (authenticated) {
                return adaptor.createResponse(getPath(), (User) getSessionUser(), Arrays.asList("ID", "NAME", "API_KEY"));
            } else {
                throw new AccessDeniedException("Login incorrect!");
            }
        }
    }

    protected final HtmlView createLoginView(StatusType statusType, String text) {
        invalidateSession();
        HtmlView lv = createLoginView();
        lv.setStatus(statusType, text);
        return lv;
    }

    protected void invalidateSession() {
        path.invalidateSession();
    }

    protected HtmlView createLoginView() {
        if (getPath().getFormFields().containsKey("_REGISTER")) {
            return createLoginView(true);
        } else {
            return createLoginView(false);
        }
    }

    protected boolean isRegistrationRequired() {
        return Config.instance().getBooleanProperty("swf.application.requires.registration", false);
    }

    protected HtmlView createLoginView(boolean registrationInProgress) {
        invalidateSession();
        return new LoginView(getPath(), isRegistrationRequired(), registrationInProgress);
    }

    @SuppressWarnings("unchecked")
    public <U extends User> U getSessionUser() {
        return (U) getPath().getSessionUser();
    }

    @RequireLogin(false)
    public View logout() {
        invalidateSession();
        return new RedirectorView(getPath(), "", "login");
    }

    @RequireLogin(false)
    public View index() {
        return new RedirectorView(getPath(), "dashboard");
    }

    public DashboardView dashboard() {
        return Controller.dashboard(getPath());
    }

    public DashboardView dashboard(HtmlView aContainedView) {
        return Controller.dashboard(getPath(), aContainedView);
    }

    protected static DashboardView dashboard(Path currentPath) {
        return new DashboardView(currentPath);
    }

    protected static DashboardView dashboard(Path currentPath, HtmlView aContainedView) {
        DashboardView dashboard = dashboard(currentPath);
        dashboard.setChildView(aContainedView);
        return dashboard;
    }

    @RequireLogin(false)
    public View resources(String name) throws IOException {
        Path p = getPath();
        if (name.startsWith("/config/")) {
            return new BytesView(p, "Access Denied!".getBytes());
        }

        InputStream is = null ;
        File dir = new File("./src/main/resources");
        if (dir.isDirectory() ){
            File resource = new File(dir + "/" + name);
            if (resource.exists() && resource.isFile()){
                try {
                    is = new FileInputStream(new File(dir + "/" + name));
                }catch (Exception ex){
                    is = null;
                }
            }
        }
        if (is == null) {
            is = getClass().getResourceAsStream(name);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read = 0;
        try {
            if (is != null) {
                while ((read = is.read(buffer)) >= 0) {
                    baos.write(buffer, 0, read);
                }
            } else {
                throw new AccessDeniedException("No such resource!");
            }
        } catch (IOException ex) {
            //
        }

        //p.getResponse().setDateHeader("Expires", DateUtils.addHours(System.currentTimeMillis(), 24 * 365 * 15));
        return new BytesView(getPath(), baos.toByteArray(), MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(name));
    }

    public <M extends Model> JSONObject autocompleteJSON(Class<M> modelClass, Expression baseWhereClause, String fieldName, String value) {
        FormatHelper<JSONObject> fh = FormatHelper.instance(MimeType.APPLICATION_JSON, "entries", true);

        ModelReflector<M> reflector = ModelReflector.instance(modelClass);
        ColumnDescriptor fd = reflector.getColumnDescriptor(fieldName);

        String columnName = fd.getName();

        Expression where = new Expression(reflector.getPool(), Conjunction.AND);

        //where.add(baseWhereClause); baseWhereClause may have virtual columns,
        int maxRecordsToGet = MAX_LIST_RECORDS;
        if (!ObjectUtil.isVoid(value)) {
            if (reflector.getIndexedColumns().contains(columnName)) {
                LuceneIndexer indexer = LuceneIndexer.instance(reflector);
                StringBuilder qry = new StringBuilder();
                qry.append("( ");
                StringTokenizer tok = new StringTokenizer(QueryParser.escape(value));
                while (tok.hasMoreElements()){
                    qry.append(columnName).append(":");
                    qry.append(tok.nextElement());
                    qry.append("*");
                    if (tok.hasMoreElements()){
                        qry.append(" AND ");
                    }
                }

                qry.append(")");
                Query q = indexer.constructQuery(qry.toString());
                List<Long> topRecords = indexer.findIds(q, maxRecordsToGet);
                int numRecordRetrieved = topRecords.size();
                if (numRecordRetrieved > 0) {
                    Expression idExpression = Expression.createExpression(reflector.getPool(), "ID", Operator.IN, topRecords.toArray());
                    where.add(idExpression);
                } else {
                    return fh.getRoot();
                }
            } else if (!reflector.isFieldVirtual(fieldName)) {
                where.add(new Expression(reflector.getPool(), reflector.getJdbcTypeHelper().getLowerCaseFunction() + "(" + columnName + ")", Operator.LK, new BindVariable(reflector.getPool(), "%" + value.toLowerCase() + "%")));
            }
        }
        Select q = new Select().from(modelClass);
        q.where(where).orderBy(reflector.getOrderBy());
        List<M> records = q.execute(modelClass, maxRecordsToGet, new DefaultModelFilter<M>(modelClass));
        Iterator<M> i = records.iterator();
        while (i.hasNext()) {
            M m = i.next();
            if (!baseWhereClause.eval(m)) {
                i.remove();
            } else if (reflector.isFieldVirtual(fieldName)) {
                String fieldValue = reflector.get(m, fieldName);
                if (!ObjectUtil.isVoid(value) ) {
                    StringTokenizer tokenizer = new StringTokenizer(value);
                    boolean pass = true;
                    while (tokenizer.hasMoreElements()){
                        pass = pass && fieldValue.toLowerCase().contains(tokenizer.nextToken().toLowerCase());
                    }
                    if (!pass) {
                        i.remove();
                    }
                }
            }
        }
        Method fieldGetter = reflector.getFieldGetter(fieldName);
        TypeConverter<?> converter = Database.getJdbcTypeHelper(reflector.getPool()).getTypeRef(fieldGetter.getReturnType()).getTypeConverter();

        for (M record : records) {
            try {
                createEntry(reflector, fh, converter.toString(fieldGetter.invoke(record)), record.getId());
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            } catch (IllegalArgumentException ex) {
                throw new RuntimeException(ex);
            } catch (InvocationTargetException ex) {
                throw new RuntimeException(ex);
            }
        }
        return fh.getRoot();
    }

    public <M extends Model> View autocomplete(Class<M> modelClass, Expression baseWhereClause, String fieldName, String value) {
        JSONObject doc = autocompleteJSON(modelClass, baseWhereClause, fieldName, value);
        Config.instance().getLogger(getClass().getName()).info(doc.toString());
        return new BytesView(path, String.valueOf(doc).getBytes());
    }

    private void createEntry(ModelReflector<? extends Model> reflector, FormatHelper<JSONObject> doc, Object name, Object id) {
        JSONObject elem = doc.createArrayElement("entry");
        FormatHelper<JSONObject> elemHelper = FormatHelper.instance(elem);
        elemHelper.setAttribute("name", Database.getJdbcTypeHelper(reflector.getPool()).getTypeRef(name.getClass()).getTypeConverter().toString(name));
        elemHelper.setAttribute("id", Database.getJdbcTypeHelper(reflector.getPool()).getTypeRef(id.getClass()).getTypeConverter().toString(id));
    }

    protected Map<String, Object> getFormFields() {
        return getPath().getFormFields();
    }

    private List<Sheet> getSheetsToImport(Workbook book, ImportSheetFilter filter) {
        List<Sheet> sheets = new ArrayList<Sheet>();
        for (int i = 0; i < book.getNumberOfSheets(); i++) {
            Sheet sheet = book.getSheetAt(i);
            if (filter.filter(sheet)) {
                sheets.add(sheet);
            }
        }
        return sheets;
    }

    protected void importxls(InputStream in, ImportSheetFilter filter) {
        List<ModelReflector<? extends Model>> modelReflectorsOfImportedTables = new ArrayList<ModelReflector<? extends Model>>();
        Workbook book = null;
        try {
            book = new XSSFWorkbook(in);
            for (Sheet sheet : getSheetsToImport(book, filter)) {
                Table<? extends Model> table = getTable(sheet);
                if (table == null) {
                    continue;
                }
                Config.instance().getLogger(getClass().getName()).info("Importing:" + table.getTableName());
                try {
                    modelReflectorsOfImportedTables.add(table.getReflector());
                    importxls(sheet, table.getModelClass());
                } catch (Exception e) {
                    for (ModelReflector<? extends Model> ref : modelReflectorsOfImportedTables) {
                        Database.getInstance().getCache(ref).clear();
                    }
                    if (!(e instanceof RuntimeException)) {
                        throw new RuntimeException(e);
                    } else {
                        throw (RuntimeException) e;
                    }
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            try {
                if (book != null) {
                    book.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public View importxls() {
        return importxls(getDefaultImportSheetFilter());
    }

    public final View importxls(ImportSheetFilter filter) {
        HttpServletRequest request = getPath().getRequest();

        if (request.getMethod().equalsIgnoreCase("GET")) {
            return dashboard(new FileUploadView(getPath()));
        } else {
            Map<String, Object> formFields = getFormFields();
            if (!formFields.isEmpty()) {
                InputStream in = (InputStream) formFields.get("datafile");
                if (in == null) {
                    throw new RuntimeException("Nothing uploaded!");
                }
                importxls(in, filter);
            }
            return back();
        }

    }

    @RequireLogin(false)
    public RedirectorView back() {
        RedirectorView v = new RedirectorView(getPath());
        v.setRedirectUrl(getPath().getBackTarget());
        return v;
    }

    protected static <M extends Model> Table<M> getTable(Sheet sheet) {
        String tableName = StringUtil.underscorize(sheet.getSheetName());
        Table<M> table = Database.getTable(tableName);
        return table;
    }

    protected <M extends Model> XLSModelReader<M> getXLSModelReader(Class<M> modelClass) {
        return new XLSModelReader<M>(modelClass);
    }

    protected <M extends Model> void importxls(Sheet sheet, Class<M> modelClass) {
        XLSModelReader<M> modelReader = getXLSModelReader(modelClass);
        importRecords(modelReader.read(sheet), modelClass);
    }

    protected <M extends Model> void importRecords(List<M> records, Class<M> modelClass) {
        for (M m : records) {
            importRecord(m, modelClass);
        }
    }

    protected <M extends Model> void importRecord(M record, Class<M> modelClass) {
        getPath().fillDefaultsForReferenceFields(record, modelClass);
        save(record, modelClass);
    }

    protected <M extends Model> void save(M record, Class<M> modelClass) {
        if (record.getRawRecord().isNewRecord()) {
            record.setCreatorUserId(getPath().getSessionUserId());
            record.setCreatedAt(null);
        }
        if (record.isDirty()) {
            record.setUpdaterUserId(getPath().getSessionUserId());
            record.setUpdatedAt(null);
        }
        record.save(); //Allow extensions to fill defaults etc.

        if (getSessionUser() != null && (!record.isAccessibleBy(getSessionUser())
                || !getPath().getModelAccessPath(modelClass).canAccessControllerAction("save", String.valueOf(record.getId())))) {
            Database.getInstance().getCache(ModelReflector.instance(modelClass)).clear();
            throw new AccessDeniedException();
        }
    }

    public View exportxls() {
        Workbook wb = new XSSFWorkbook();
        for (String pool : ConnectionManager.instance().getPools()) {
            Map<String, Table<? extends Model>> tables = Database.getTables(pool);
            for (String tableName : tables.keySet()) {
                Table<? extends Model> table = tables.get(tableName);
                EXPORTABLE exportable = table.getReflector().getAnnotation(EXPORTABLE.class);
                if (table.isReal() && (exportable == null || exportable.value())) {
                    Config.instance().getLogger(getClass().getName()).info("Exporting:" + table.getTableName());
                    exportxls(table.getModelClass(), wb);
                }
            }
        }
        try {
            String baseFileName = "db" + new SimpleDateFormat("yyyyMMddHHmm").format(new java.util.Date(System.currentTimeMillis()));

            ByteArrayOutputStream os = new ByteArrayOutputStream();

            ZipOutputStream zos = new ZipOutputStream(os);
            zos.putNextEntry(new ZipEntry(baseFileName + ".xlsx"));
            wb.write(zos);
            zos.closeEntry();
            zos.close();

            return new BytesView(getPath(), os.toByteArray(), MimeType.APPLICATION_ZIP, "content-disposition", "attachment; filename=" + baseFileName + ".zip");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    protected final SWFLogger cat = Config.instance().getLogger(getClass().getName());

    protected class DefaultModelFilter<M extends Model> implements Select.ResultFilter<M> {

        Select.AccessibilityFilter<M> defaultFilter = new Select.AccessibilityFilter<M>();
        Class<M> modelClass = null;

        public DefaultModelFilter(Class<M> modelClass) {
            super();
            this.modelClass = modelClass;
        }

        @Override
        public boolean pass(M record) {
            Timer timer = cat.startTimer("DefaultModelFilter.pass", Config.instance().isTimerAdditive());
            try {
                return defaultFilter.pass(record) && getPath().getModelAccessPath(modelClass).canAccessControllerAction("index",
                        StringUtil.valueOf(record.getId()));
            } finally {
                timer.stop();
            }
        }
    }
    protected <M extends Model> void exportxls(Class<M> modelClass, Workbook wb) {
        exportxls(modelClass,wb,null);
    }
    protected <M extends Model> void exportxls(Class<M> modelClass, Workbook wb, List<M> records) {
        ModelReflector<M> reflector = ModelReflector.instance(modelClass);
        List<String> fieldsIncluded = reflector.getFields();
        Iterator<String> fieldIterator = fieldsIncluded.iterator();
        int numUniqueKeys = reflector.getUniqueKeys().size();
        while (fieldIterator.hasNext()) {
            String field = fieldIterator.next();
            EXPORTABLE exportable = reflector.getAnnotation(reflector.getFieldGetter(field), EXPORTABLE.class);
            if (exportable != null) {
                if (!exportable.value()) {
                    fieldIterator.remove();
                }
            } else if (reflector.isHouseKeepingField(field)) {
                //if (!field.equals("ID") || numUniqueKeys > 0){
                if (!field.equals("ID")) {
                    if ((!Config.instance().getBooleanProperty("swf.listview.housekeeping.show", false) && reflector.isHouseKeepingField(field))
                            || !reflector.isFieldVisible(field)) {
                        fieldIterator.remove();
                    }
                }
            }
        }
        exportxls(modelClass, wb, fieldsIncluded,records);
    }

    protected <M extends Model> void exportxls(Class<M> modelClass, Workbook wb, List<String> fieldsIncluded , List<M> records) {
        List<M> list = records ;
        if (list == null){
            list = new Select().from(modelClass).where(getPath().getWhereClause(modelClass)).execute(modelClass, new DefaultModelFilter<M>(modelClass));
        }
        getXLSModelWriter(modelClass).write(list, wb, fieldsIncluded, new HashSet<>(), new HashMap<>());
    }

    protected <M extends Model> XLSModelWriter<M> getXLSModelWriter(Class<M> modelClass) {
        return new XLSModelWriter<M>(modelClass);
    }

    public static interface ImportSheetFilter {

        public boolean filter(Sheet sheet);
    }

    protected ImportSheetFilter getDefaultImportSheetFilter() {
        return new ImportSheetFilter() {

            @Override
            public boolean filter(Sheet sheet) {
                Table<? extends Model> table = getTable(sheet);
                if (table != null) {
                    return true;
                }
                return false;
            }

        };
    }

    public enum CacheOperation{
        SET,
        GET,
        CLEAR,
    }
    public static final String GET_CACHED_RESULT_EXTENSION = "get.cached.result";
    public View getCachedResult(){
        ObjectHolder<View> holder = new ObjectHolder<>(null);
        Registry.instance().callExtensions(GET_CACHED_RESULT_EXTENSION,CacheOperation.GET,getPath(),holder);
        return holder.get();
    }

    public static final String SET_CACHED_RESULT_EXTENSION = "set.cached.result";
    public void setCachedResult(View view){
        ObjectHolder<View> holder = new ObjectHolder<>(view);
        Registry.instance().callExtensions(SET_CACHED_RESULT_EXTENSION,CacheOperation.SET,getPath(),holder);
    }

    public static final String CLEAR_CACHED_RESULT_EXTENSION = "clear.cached.result";
    public View clearCachedResult(){
        Registry.instance().callExtensions(CLEAR_CACHED_RESULT_EXTENSION,CacheOperation.CLEAR,getPath());
        return new BytesView(getPath(),"OK".getBytes());
    }
}
