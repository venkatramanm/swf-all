package com.venky.swf.plugins.templates.util.templates;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Message.Builder;
import com.google.firebase.messaging.MessagingErrorCode;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.venky.cache.Cache;
import com.venky.core.io.ByteArrayInputStream;
import com.venky.core.io.SeekableByteArrayOutputStream;
import com.venky.core.string.Inflector;
import com.venky.core.util.MultiException;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.io.json.JSONModelWriter;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.templates.db.model.User;
import com.venky.swf.plugins.templates.db.model.alerts.Alert;
import com.venky.swf.plugins.templates.db.model.alerts.Device;
import com.venky.swf.routing.Config;
import freemarker.cache.NullCacheStorage;
import freemarker.core.ArithmeticEngine;
import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateNotFoundException;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.w3c.tidy.Tidy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class TemplateEngine {


    private static Cache<String,TemplateEngine>  instance = new Cache<String, TemplateEngine>() {
        @Override
        protected TemplateEngine getValue(String directory) {
            return new TemplateEngine(directory);
        }
    };
    public static TemplateEngine getInstance(){
        return getInstance(null);
    }
    public static TemplateEngine getInstance(String directory){
        return instance.get(directory == null? Config.instance().getProperty("swf.ftl.dir") : directory);
    }
    Configuration cfg = null;
    private TemplateEngine(String directory){
        cfg = new Configuration(Configuration.VERSION_2_3_28);
        try {
            File dir = null;
            if (!ObjectUtil.isVoid(directory)) {
                dir = new File(directory);
            }
            if (dir != null && dir.exists() && dir.isDirectory()){
                cfg.setDirectoryForTemplateLoading(dir);
            }else{
                cfg.setClassForTemplateLoading(TemplateEngine.class, directory);
            }
        }catch (Exception ex){
            cfg.setClassForTemplateLoading(TemplateEngine.class, "/templates");
        }
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setLocalizedLookup(false);
        cfg.setWrapUncheckedExceptions(true);
        ArithmeticEngine engine = ArithmeticEngine.BIGDECIMAL_ENGINE;
        engine.setMinScale(2);
        engine.setMaxScale(2);
        cfg.setArithmeticEngine(engine);
        cfg.setCacheStorage(new NullCacheStorage()); //
        cfg.setSharedVariable("to_words",new ToWords());

    }
    public String publish(String templateName, Map<String,Object> root) {
        StringWriter writer = new StringWriter();
        Config.instance().getLogger(getClass().getName()).info("Data for " + templateName +":" + root.toString());
        publish(templateName,root,writer);
        return writer.toString();
    }
    public void publish(String templateName, Map<String,Object> root, Writer output){
        try {
            Template template = cfg.getTemplate(templateName);
            publish(template,root,output);
        } catch (IOException ex) {
            throw new RuntimeException(root +"\n"+ ex.getMessage(),ex);
        }
    }
    public void publish (Template template, Map<String,Object> root,Writer output){
        try {
            for (String key : Config.instance().getPropertyKeys("swf.*host")){
                putEnvKey(root,key,Config.instance().getProperty(key));
            }

            Config.instance().getLogger(getClass().getName()).info(root.toString());
            template.process(root,output);
        }catch (IOException | TemplateException ex) {
            throw new RuntimeException(root +"\n"+ ex.getMessage(),ex);
        }
    }

    public byte[] htmlToPdf(byte[] htmlBytes){
        StringWriter tidyWriter = new StringWriter();

        Tidy tidy =  new Tidy();
        tidy.setXHTML(true);
        tidy.parse(new ByteArrayInputStream(htmlBytes), tidyWriter);


        if (tidyWriter.getBuffer().length() > 0){
            try (SeekableByteArrayOutputStream os = new SeekableByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.withHtmlContent(tidyWriter.toString(), ".");
                builder.toStream(os);
                builder.run();
                return os.toByteArray();
            } catch (Exception e) {
                return new byte[]{};
            }
        }
        return new byte[]{};
    }
    public boolean exists(String templateName){
        try {
            return (null != cfg.getTemplate(templateName,null,null,false,true));
        }catch (Exception ex){
            return false;
        }
    }
    public enum TransportType {
        MAIL,
        PUSH,
        UI,
        WHATSAPP
    }
    public void send(User user, String subject, String templateName, List<Model> entities, Map<Class<? extends Model>, List<String>> entityFieldMap, TransportType ... transportType){
        send(user,subject,templateName,entities,entityFieldMap,new HashMap<>(),transportType);
    }
    public void send(User user, String subject, String templateName, List<Model> entities,TransportType ... transportType){
        send(user,subject,templateName,entities,null,transportType);
    }
    public void send(User user, String subject, String templateName, List<Model> entities, Map<Class<? extends Model>, List<String>> entityFieldMap, Map<String,Object> otherVariables, TransportType ... transportType){
        send(user,subject,templateName, createEntityMap(entities),entityFieldMap,otherVariables,transportType);
    }
    public Map<String,Object> createEntityMap(List<Model> entities) {
        Map<String, Object> entityMap = new HashMap<>();
        Set<Class<?>> pluralEntities = new HashSet<>();
        for (Model entity :entities){
            String keyName = entity.getReflector().getModelClass().getSimpleName();
            boolean isPlural = false;
            if (pluralEntities.contains(entity.getReflector().getModelClass())){
                isPlural = true;
                keyName = Inflector.pluralize(keyName);
            }

            Object value = entityMap.get(keyName);
            if (value == null){
                entityMap.put(keyName,entity);
            }else{
                if (!isPlural) {
                    pluralEntities.add(entity.getReflector().getModelClass());
                    List<Model> tmp  = new ArrayList<>();
                    tmp.add((Model)value);
                    tmp.add(entity);
                    entityMap.remove(keyName);
                    entityMap.put(Inflector.pluralize(keyName),tmp);
                }else {
                    ((List<Model>)value).add(entity);
                }
            }
        }
        return entityMap;
    }

    public Map<String,Object> formatEntityMap(Map<String,Object> entityMap, Map<Class<? extends Model>, List<String>> entityFieldMap) {
        Map<String, Object> root = new HashMap<>();
        for (String entityName :entityMap.keySet()){
            Object entityOrEntityList = entityMap.get(entityName);
            List<Model> entityList  = entityOrEntityList instanceof List ? (List<Model>)entityOrEntityList : null;
            Model entity = entityOrEntityList instanceof Model ? (Model)entityOrEntityList : null;

            if (entity != null){
                root.put(entityName,format(entity,entityFieldMap));
            }else if (entityList != null){
                List<JSONObject> out = new ArrayList<>();
                for (Model e : entityList){
                    out.add(format(e,entityFieldMap));
                }
                root.put(entityName,out);
            }
        }
        return root;
    }
    public JSONObject format(Model entity,Map<Class<? extends Model>, List<String>> entityFieldMap){
        JSONObject into = new JSONObject();
        JSONModelWriter<Model> modelJSONModelWriter = new JSONModelWriter<>(entity.getReflector().getModelClass());
        modelJSONModelWriter.write(entity,into,entityFieldMap == null ? null : entityFieldMap.get(entity.getReflector().getModelClass()), new HashSet<>(),
                entityFieldMap == null ? new HashMap<>() : entityFieldMap);
        return  into;
    }
    public void send(User user, String subject, String templateName, Map<String,Object> entityMap,Map<Class<? extends Model>, List<String>> entityFieldMap, Map<String,Object> otherVariables, TransportType ... transportType ){
        Map<String, Object> root = formatEntityMap(entityMap,entityFieldMap);
        if (otherVariables != null){
            root.putAll(otherVariables);
        }

        send(user,subject,templateName,root,transportType);
    }

    public void putEnvKey(Map<String,Object> root, String key, String value){
        String[] path = key.split("\\.");
        if (path.length == 0){
            return;
        }
        Map previousLevelMap = (Map)root.get("env");
        if (previousLevelMap == null){
            previousLevelMap =  new HashMap<>();
            root.put("env",previousLevelMap);
        }
        for (int i = 0 ; i < path.length ; i ++ ){
            Object o = previousLevelMap.get(path[i]);
            if (o == null) {
                o = new HashMap<>();
                previousLevelMap.put(path[i], o);
            }
            if (i < path.length - 1){
                previousLevelMap = (Map)o;
            }else {
                previousLevelMap.put(path[i],value);
                break;
            }
        }
    }
    public void send(User user, String subject, String templateName, Map<String,Object> root,TransportType ... applicableTransports){
        TransportType []  transportTypes = (applicableTransports == null || applicableTransports.length == 0 ) ? TransportType.values() : applicableTransports;
        for (TransportType transportType : transportTypes) {
            _send(transportType,user,subject,transportType.toString() + "_"+templateName,root);
        }
    }
    private void _send(TransportType transportType,User user, String subject, String templateName, Map<String,Object> root){
        if (user == null || !user.isNotificationEnabled()){
            return;
        }
        if (!exists(templateName)){
            return;
        }

        try {
            switch (transportType){
                case MAIL:
                    if (user.isEmailNotificationEnabled() ){
                        mail(user,subject,templateName,root);
                    }
                    break;
                case PUSH:
                    push(user,subject,templateName,root);
                    break;
                case UI:
                    logAlert(user,subject,templateName,root);
                    break;
                case WHATSAPP:
                    sendWhatsApp(user,subject,templateName,root);
            }
        }catch (Exception ex){
            Config.instance().getLogger(getClass().getName()).log(Level.WARNING,"Could not send message to " + user.getName() , ex );
        }
    }

    private void sendWhatsApp(User user, String subject, String templateName, Map<String, Object> root) throws UnsupportedEncodingException {
        if (!user.isWhatsAppNotificationEnabled()) {
            return;
        }
        String phoneNumber = sanitize(user.getPhoneNumber());
        if (ObjectUtil.isVoid(phoneNumber)) {
            return;
        }
        String message = publish(templateName,root);
        String whatsAppProviderUrl = Config.instance().getProperty("whatsapp.url");
        if (ObjectUtil.isVoid(whatsAppProviderUrl)){
            return;
        }
        JSONObject input = new JSONObject();
        input.put("userid",Config.instance().getProperty("whatsapp.userid"));
        input.put("password",Config.instance().getProperty("whatsapp.password"));
        if (ObjectUtil.isVoid(input.get("userid")) || ObjectUtil.isVoid(input.get("password"))){
            return;
        }
        input.put("method","SendMessage");

        input.put("auth_scheme","plain");
        input.put("v",1.1);
        input.put("send_to",phoneNumber);
        //input.put("msg", URLEncoder.encode(message, "UTF-8"));
        input.put("msg", message);
        input.put("msg_type","HSM");
        input.put("format","json");
        input.put("isTemplate",true);
        Call<JSONObject> call = new Call<JSONObject>().url(whatsAppProviderUrl).inputFormat(InputFormat.FORM_FIELDS).
                input(input).method(HttpMethod.GET);
        JSONObject response = call.getResponseAsJson();
        if (response != null){
            response = (JSONObject) response.get("response");
        }
        if (call.hasErrors() || (ObjectUtil.equals(response.get("status"),"error"))){
            throw  new RuntimeException("Could not send whatsapp notification to " + phoneNumber);
        }
        // May need to adaptorize later. !! this is GUPSHUP adaptor
    }
    public String sanitize(String phoneNumber){
        if (ObjectUtil.isVoid(phoneNumber)){
            return "";
        }
        if (phoneNumber.charAt(0) == '+'){
            String ret = phoneNumber.substring(1);
            if (ret.length() == 12){
                return ret;
            }
        }
        return "";
    }

    protected void logAlert(User user, String subject, String templateName, Map<String,Object> root) {
        Alert alert = Database.getTable(Alert.class).newRecord();
        alert.setTemplate(templateName);
        alert.setAlertedAt(new Timestamp(System.currentTimeMillis()));
        alert.setSubject(subject);
        alert.setMessage(publish(templateName, root));
        alert.setUserId(user.getId());
        alert.save();
    }


    protected void mail(User user, String subject, String templateName, Map<String,Object> root){
        TaskManager.instance().executeAsync(new MailMerge(user,subject,templateName,root));
    }
    
    public static abstract class TemplateMergeTask implements   Task{
        long id ;
        String  subject;
        String templateName ;
        Map<String,Object> root;
        public TemplateMergeTask(){

        }
        public TemplateMergeTask(User user,String subject,String templateName, Map<String,Object> root){
            id = user.getId();
            this.subject = subject;
            this.templateName = templateName;
            this.root = root;
        }
    }

    public static class MailMerge extends TemplateMergeTask{
        public MailMerge(){

        }
        public MailMerge(User user,String subject,String templateName, Map<String,Object> root){
            super(user,subject,templateName,root);
        }

        @Override
        public void execute() {
            User mailUser = Database.getTable(User.class).get(id);
            if (!mailUser.getUserEmails().isEmpty()){
                mailUser.sendMail(subject,TemplateEngine.getInstance().publish(templateName,root));
            }
        }
    }


    protected void push(User user, String subject, String templateName, Map<String,Object> root){

        JSONObject notification = (JSONObject)JSONValue.parse(TemplateEngine.getInstance().publish(templateName,root));
        for (Device device : user.getDevices()) {
            JSONObject payload = new JSONObject();
            payload.put("notification",notification);
            if (!notification.containsKey("title")){
                notification.put("title",subject);
            }
            payload.put("to",device.getId());
            TaskManager.instance().executeAsync(new PushNotifier(payload));
        }
    }
    static boolean fbinitialized = false;
    private synchronized static void initializeAndroid(){
        if (fbinitialized){
            return;
        }
        try {
            String file = Config.instance().getProperty("push.service.account.json");
            String url = Config.instance().getProperty("push.service.database.url");
            if (ObjectUtil.isVoid(file)){
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setDatabaseUrl(url).build();

            FirebaseApp.initializeApp(options);
            fbinitialized = true;
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }

    }

    public static class PushNotifier implements Task {
        JSONObject payload;
        public PushNotifier(){

        }
        public PushNotifier(JSONObject payload){
            this.payload = payload;
        }

        private boolean isWebPush(Device device){
            return device.getSubscriptionJson().containsKey("endpoint");
        }

        private void pushWeb(Device device){
            JSONObject subscriptionJson = device.getSubscriptionJson();

            Subscription subscription = new Subscription();
            {
                JSONObject keys = (JSONObject) subscriptionJson.get("keys");
                subscription.setAuth(keys.get("auth").toString());
                subscription.setKey(keys.get("p256dh").toString());
            }
            subscription.setEndpoint(subscriptionJson.get("endpoint").toString());



            Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getUserPublicKey(),
                    subscription.getAuthAsBytes(),
                    payload.toString().getBytes()
            );

            // Instantiate the push service, no need to use an API key for Push API
            PushService pushService = new PushService();
            try {
                String privateKey = Config.instance().getProperty("push.server.private.key");
                String publicKey = Config.instance().getProperty("push.server.public.key");

                pushService.setPrivateKey(Utils.loadPrivateKey(privateKey));
                pushService.setPublicKey(Utils.loadPublicKey(publicKey));
                pushService.send(notification);
            } catch (Exception e) {
                MultiException ex= new MultiException("Device : " + device.getId() + " issue in sending push message.");
                ex.add(e);
                throw ex;
            }

        }

        private void pushAndroid(Device device){
            initializeAndroid();
            JSONObject subscriptionJson = device.getSubscriptionJson();
            String token  = (String)subscriptionJson.get("token");
            Builder messageBuilder = Message.builder();

            JSONObject notification = (JSONObject)payload.get("notification");
            JSONObject data = notification == null ? null : (JSONObject)notification.remove("data");
            for (JSONObject object: new JSONObject[]{ notification , data }){
                if (object != null) {
                    for (Object key : object.keySet()) {
                        messageBuilder.putData(key.toString(), object.get(key).toString());
                    }
                }
            }
            messageBuilder.setToken(token);
            Message message = messageBuilder.build();
            String response = null;
            try {
                response = FirebaseMessaging.getInstance().send(message);
                Config.instance().getLogger(getClass().getName()).info("Successfully sent message: "  + response);
            } catch (FirebaseMessagingException e) {
                if (e.getErrorCode().equals(ErrorCode.NOT_FOUND)){
                    device.destroy();// Not subscribed any more.
                }
            }
        }

        @Override
        public void execute() {
            Device device = Database.getTable(Device.class).get(ModelReflector.instance(Device.class).getJdbcTypeHelper().
                        getTypeRef(Long.class).getTypeConverter().valueOf(payload.get("to")));
            if (device == null){
                return;
            }

            if (isWebPush(device)){
                pushWeb(device);
            }else {
                pushAndroid(device);
            }


        }
    }

    public static class Subscription {
        // Getters and setters for e.g. endpoint
        String auth;
        String key;
        String endpoint;
        public Subscription(){
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
        }

        public void setAuth(String auth) {
            this.auth = auth;
        }

        public String getAuth() {
            return auth;
        }

        /**
         * Returns the base64 encoded auth string as a byte[]
         */
        public byte[] getAuthAsBytes() {
            return Base64.getDecoder().decode(getAuth());
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        /**
         * Returns the base64 encoded public key string as a byte[]
         */
        public byte[] getKeyAsBytes() {
            return Base64.getDecoder().decode(getKey());
        }

        /**
         * Returns the base64 encoded public key as a PublicKey object
         */
        public PublicKey getUserPublicKey(){
            try {
                KeyFactory kf = KeyFactory.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
                ECNamedCurveParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec("secp256r1");
                ECPoint point = ecSpec.getCurve().decodePoint(getKeyAsBytes());
                ECPublicKeySpec pubSpec = new ECPublicKeySpec(point, ecSpec);

                return kf.generatePublic(pubSpec);
            }catch(Exception ex){
                throw new RuntimeException(ex);
            }

        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getEndpoint() {
            return endpoint;
        }
    }
}
