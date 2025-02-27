package com.eveningoutpost.dexdrip.cgm.carelinkfollow.client;

import com.eveningoutpost.dexdrip.cgm.carelinkfollow.message.ActiveNotification;
import com.eveningoutpost.dexdrip.cgm.carelinkfollow.message.ClearedNotification;
import com.eveningoutpost.dexdrip.cgm.carelinkfollow.message.CountrySettings;
import com.eveningoutpost.dexdrip.cgm.carelinkfollow.message.Marker;
import com.eveningoutpost.dexdrip.cgm.carelinkfollow.message.MonitorData;
import com.eveningoutpost.dexdrip.cgm.carelinkfollow.message.Profile;
import com.eveningoutpost.dexdrip.cgm.carelinkfollow.message.RecentData;
import com.eveningoutpost.dexdrip.cgm.carelinkfollow.message.SensorGlucose;
import com.eveningoutpost.dexdrip.cgm.carelinkfollow.message.User;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


/**
 * CareLink Follower Client
 * - API client for communication with CareLink
 */
public class CareLinkClient {
    protected String carelinkUsername;
    protected String carelinkPassword;
    protected String carelinkCountry;
    protected static final String CARELINK_CONNECT_SERVER_EU = "carelink.minimed.eu";
    protected static final String CARELINK_CONNECT_SERVER_US = "carelink.minimed.com";
    protected static final String CARELINK_LANGUAGE_EN = "en";
    protected static final String CARELINK_LOCALE_EN = "en";
    protected static final String CARELINK_AUTH_TOKEN_COOKIE_NAME = "auth_tmp_token";
    protected static final String CARELINK_TOKEN_VALIDTO_COOKIE_NAME = "c_token_valid_to";
    protected static final int AUTH_EXPIRE_DEADLINE_MINUTES = 1;

    protected static final SimpleDateFormat[] ZONED_DATE_FORMATS = {
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
    };

    protected OkHttpClient httpClient = null;
    protected boolean loginInProcess = false;
    protected int lastResponseCode;

    public int getLastResponseCode() {
        return lastResponseCode;
    }

    private boolean lastLoginSuccess;

    public boolean getLastLoginSuccess() {
        return lastLoginSuccess;
    }

    private boolean lastDataSuccess;

    public boolean getLastDataSuccess() {
        return lastDataSuccess;
    }

    private String lastErrorMessage;

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    private String lastStackTraceString;

    public String getLastStackTraceString() {
        return lastStackTraceString;
    }

    //Session info
    protected User sessionUser;

    public User getSessionUser() {
        return sessionUser;
    }

    protected Profile sessionProfile;

    public Profile getSessionProfile() {
        return sessionProfile;
    }

    protected CountrySettings sessionCountrySettings;

    public CountrySettings getSessionCountrySettings() {
        return sessionCountrySettings;
    }

    protected MonitorData sessionMonitorData;

    public MonitorData getSessionMonitorData() {
        return sessionMonitorData;
    }

    protected enum RequestType {
        HtmlGet(),
        HtmlPost(),
        Json()
    }

    public CareLinkClient(String carelinkUsername, String carelinkPassword, String carelinkCountry) {

        this.carelinkUsername = carelinkUsername;
        this.carelinkPassword = carelinkPassword;
        this.carelinkCountry = carelinkCountry;

        lastLoginSuccess = false;
        lastDataSuccess = false;

        //Create main http client with CookieJar
        this.httpClient = new OkHttpClient.Builder()
                .cookieJar(new SimpleOkHttpCookieJar())
                .build();

    }

    protected String careLinkServer() {
        if (this.carelinkCountry.equals("us"))
            return CARELINK_CONNECT_SERVER_US;
        else
            return CARELINK_CONNECT_SERVER_EU;
    }


    //Wrapper for common request of recent data (last 24 hours)
    public RecentData getRecentData() {

        // Force login to get basic info
        if (getAuthorizationToken() != null) {
            //New BLE Endpoint (for all US calls and all BLE devices)
            if (CountryUtils.isUS(carelinkCountry) || sessionMonitorData.isBle())
                return this.getConnectDisplayMessage(this.sessionProfile.username, this.sessionUser.getUserRole(),
                        sessionCountrySettings.blePereodicDataEndpoint);
                //Old CareLink data source
            else
                return this.getLast24Hours();
        } else {
            return null;
        }
    }

    //Authentication methods
    protected boolean executeLoginProcedure() {

        Response loginSessionResponse = null;
        Response doLoginResponse = null;
        Response consentResponse = null;


        lastLoginSuccess = false;
        loginInProcess = true;
        lastErrorMessage = "";

        try {
            //Clear cookies
            ((SimpleOkHttpCookieJar) this.httpClient.cookieJar()).deleteAllCookies();

            // Clear basic infos
            this.sessionUser = null;
            this.sessionProfile = null;
            this.sessionCountrySettings = null;
            this.sessionMonitorData = null;

            //Open login (get SessionId and SessionData)
            loginSessionResponse = this.getLoginSession();
            this.lastResponseCode = loginSessionResponse.code();

            //Login
            doLoginResponse = this.doLogin(loginSessionResponse);
            this.lastResponseCode = doLoginResponse.code();
            loginSessionResponse.close();

            //Consent
            consentResponse = this.doConsent(doLoginResponse);
            doLoginResponse.close();
            this.lastResponseCode = consentResponse.code();
            consentResponse.close();

            // Get basic infos
            this.sessionUser = this.getMyUser();
            this.sessionProfile = this.getMyProfile();
            this.sessionCountrySettings = this.getMyCountrySettings();
            this.sessionMonitorData = this.getMonitorData();

        } catch (Exception e) {
            lastErrorMessage = e.getClass().getSimpleName() + ":" + e.getMessage();
            // lastStackTraceString = Log.getStackTraceString(e);
        } finally {
            loginInProcess = false;
        }

        // Set login success if everything was ok:
        if (this.sessionUser != null && this.sessionProfile != null && this.sessionCountrySettings != null && this.sessionMonitorData != null)
            lastLoginSuccess = true;
            //Clear cookies if error occured during logon
        else
            ((SimpleOkHttpCookieJar) this.httpClient.cookieJar()).deleteAllCookies();

        return lastLoginSuccess;

    }

    protected Response getLoginSession() throws IOException {

        HttpUrl url = null;
        Request.Builder requestBuilder = null;


        url = new HttpUrl.Builder()
                .scheme("https")
                .host(this.careLinkServer())
                .addPathSegments("patient/sso/login")
                .addQueryParameter("country", this.carelinkCountry)
                .addQueryParameter("lang", CARELINK_LANGUAGE_EN)
                .build();

        requestBuilder = new Request.Builder()
                .url(url);

        this.addHttpHeaders(requestBuilder, RequestType.HtmlGet);

        return this.httpClient.newCall(requestBuilder.build()).execute();

    }

    protected Response doLogin(Response loginSessionResponse) throws IOException {

        HttpUrl url = null;
        Request.Builder requestBuilder = null;
        RequestBody form = null;

        form = new FormBody.Builder()
                .add("sessionID", loginSessionResponse.request().url().queryParameter("sessionID"))
                .add("sessionData", loginSessionResponse.request().url().queryParameter("sessionData"))
                .add("locale", CARELINK_LOCALE_EN)
                .add("action", "login")
                .add("username", this.carelinkUsername)
                .add("password", this.carelinkPassword)
                .add("actionButton", "Log in")
                .build();

        url = new HttpUrl.Builder()
                .scheme("https")
                .host("mdtlogin.medtronic.com")
                .addPathSegments("mmcl/auth/oauth/v2/authorize/login")
                .addQueryParameter("locale", CARELINK_LOCALE_EN)
                .addQueryParameter("country", this.carelinkCountry)
                .build();

        requestBuilder = new Request.Builder()
                .url(url)
                .post(form);

        this.addHttpHeaders(requestBuilder, RequestType.HtmlGet);

        return this.httpClient.newCall(requestBuilder.build()).execute();

    }

    protected Response doConsent(Response doLoginResponse) throws IOException {

        Request.Builder requestBuilder = null;
        RequestBody form = null;
        String doLoginRespBody = null;

        doLoginRespBody = doLoginResponse.body().string();

        //Extract data for consent
        String consentUrl = this.extractResponseData(doLoginRespBody, "(form action=\")(.*)(\" method=\"POST\")", 2);
        String consentSessionData = this.extractResponseData(doLoginRespBody, "(<input type=\"hidden\" name=\"sessionData\" value=\")(.*)(\">)", 2);
        String consentSessionId = this.extractResponseData(doLoginRespBody, "(<input type=\"hidden\" name=\"sessionID\" value=\")(.*)(\">)", 2);

        //Send consent
        form = new FormBody.Builder()
                .add("action", "consent")
                .add("sessionID", consentSessionId)
                .add("sessionData", consentSessionData)
                .add("response_type", "code")
                .add("response_mode", "query")
                .build();

        requestBuilder = new Request.Builder()
                .url(consentUrl)
                .post(form);

        this.addHttpHeaders(requestBuilder, RequestType.HtmlPost);

        return this.httpClient.newCall(requestBuilder.build()).execute();

    }

    // Response parsing
    protected String extractResponseData(String respBody, String groupRegex, int groupIndex) {

        String responseData = null;
        Matcher responseDataMatcher = null;

        responseDataMatcher = Pattern.compile(groupRegex).matcher(respBody);
        if (responseDataMatcher.find()) {
            responseData = responseDataMatcher.group(groupIndex);
        }

        return responseData;

    }

    protected String getAuthorizationToken() {

        // New token is needed:
        // a) no token or about to expire => execute authentication
        // b) last response 401
        if (!((SimpleOkHttpCookieJar) httpClient.cookieJar()).contains(CARELINK_AUTH_TOKEN_COOKIE_NAME)
                || !((SimpleOkHttpCookieJar) httpClient.cookieJar()).contains(CARELINK_TOKEN_VALIDTO_COOKIE_NAME)
                || !((new Date(Date.parse(((SimpleOkHttpCookieJar) httpClient.cookieJar())
                .getCookies(CARELINK_TOKEN_VALIDTO_COOKIE_NAME).get(0).value())))
                .after(new Date(new Date(System.currentTimeMillis()).getTime()
                        + AUTH_EXPIRE_DEADLINE_MINUTES * 60000)))
                || this.lastResponseCode == 401
                || (!loginInProcess && !this.lastLoginSuccess)
        ) {
            //execute new login process
            if (this.loginInProcess || !this.executeLoginProcedure())
                return null;
        }

        //there can be only one
        return "Bearer" + " " + ((SimpleOkHttpCookieJar) httpClient.cookieJar()).getCookies(CARELINK_AUTH_TOKEN_COOKIE_NAME).get(0).value();


    }


    //CareLink data APIs

    // My user
    public User getMyUser() {
        return this.getData(this.careLinkServer(), "patient/users/me", null, null, User.class);
    }

    // My profile
    public Profile getMyProfile() {
        return this.getData(this.careLinkServer(), "patient/users/me/profile", null, null, Profile.class);
    }

    // Monitoring data
    public MonitorData getMonitorData() {
        return this.getData(this.careLinkServer(), "patient/monitor/data", null, null, MonitorData.class);
    }

    // Country settings
    public CountrySettings getMyCountrySettings() {

        Map<String, String> queryParams = null;

        queryParams = new HashMap<String, String>();
        queryParams.put("countryCode", this.carelinkCountry);
        queryParams.put("language", CARELINK_LANGUAGE_EN);

        return this.getData(this.careLinkServer(), "patient/countries/settings", queryParams, null,
                CountrySettings.class);

    }

    public RecentData getLast24Hours() {

        Map<String, String> queryParams = null;
        RecentData recentData = null;

        queryParams = new HashMap<String, String>();
        queryParams.put("cpSerialNumber", "NONE");
        queryParams.put("msgType", "last24hours");
        queryParams.put("requestTime", String.valueOf(System.currentTimeMillis()));

        try {
            recentData = this.getData(this.careLinkServer(), "patient/connect/data", queryParams, null, RecentData.class);
            if (recentData != null)
                correctTimeInRecentData(recentData);
        } catch (Exception e) {
            lastErrorMessage = e.getClass().getSimpleName() + ":" + e.getMessage();
        }

        return recentData;

    }

    // Periodic data from CareLink Cloud
    public RecentData getConnectDisplayMessage(String username, String role, String endpointUrl) {

        RequestBody requestBody = null;
        Gson gson = null;
        JsonObject userJson = null;
        RecentData recentData = null;

        // Build user json for request
        userJson = new JsonObject();
        userJson.addProperty("username", username);
        userJson.addProperty("role", role);

        gson = new GsonBuilder().create();

        requestBody = RequestBody.create(MediaType.get("application/json; charset=utf-8"), gson.toJson(userJson));

        try {
            recentData = this.getData(HttpUrl.parse(endpointUrl), requestBody, RecentData.class);
            if (recentData != null)
                correctTimeInRecentData(recentData);
        } catch (Exception e) {
            lastErrorMessage = e.getClass().getSimpleName() + ":" + e.getMessage();
        }
        return recentData;

    }

    // General data request for API calls
    protected <T> T getData(HttpUrl url, RequestBody requestBody, Class<T> dataClass) {

        Request.Builder requestBuilder = null;
        HttpUrl.Builder urlBuilder = null;
        String authToken = null;
        String responseString = null;
        Response response = null;
        Object data = null;

        this.lastDataSuccess = false;
        this.lastErrorMessage = "";

        // Get auth token
        authToken = this.getAuthorizationToken();

        if (authToken != null) {

            // Create request for URL with authToken
            requestBuilder = new Request.Builder().url(url).addHeader("Authorization", authToken);

            // Add header
            if (requestBody == null) {
                this.addHttpHeaders(requestBuilder, RequestType.Json);
            } else {
                requestBuilder.post(requestBody);
                this.addHttpHeaders(requestBuilder, RequestType.HtmlPost);
            }

            // Send request
            try {
                response = this.httpClient.newCall(requestBuilder.build()).execute();
                this.lastResponseCode = response.code();
                if (response.isSuccessful()) {
                    try {
                        responseString = response.body().string();
                        data = new GsonBuilder().create().fromJson(responseString, dataClass);
                        this.lastDataSuccess = true;
                    } catch (Exception e) {
                        lastErrorMessage = e.getClass().getSimpleName() + ":" + e.getMessage();
                    }
                }
                response.close();
            } catch (Exception e) {
                lastErrorMessage = e.getClass().getSimpleName() + ":" + e.getMessage();
            }

        }

        //Return result
        if (data != null)
            return dataClass.cast(data);
        else
            return null;

    }

    protected <T> T getData(String host, String path, Map<String, String> queryParams, RequestBody requestBody,
                            Class<T> dataClass) {

        HttpUrl.Builder urlBuilder = null;
        HttpUrl url = null;

        // Build url
        urlBuilder = new HttpUrl.Builder().scheme("https").host(host).addPathSegments(path);
        if (queryParams != null) {
            for (Map.Entry<String, String> param : queryParams.entrySet()) {
                urlBuilder.addQueryParameter(param.getKey(), param.getValue());
            }
        }

        url = urlBuilder.build();

        return this.getData(url, requestBody, dataClass);

    }

    // Http header builder for requests
    protected void addHttpHeaders(Request.Builder requestBuilder, RequestType type) {

        //Add common browser headers
        requestBuilder
                .addHeader("Accept-Language", "en;q=0.9, *;q=0.8")
                .addHeader("sec-ch-ua", "\"Google Chrome\";v=\"87\", \" Not;A Brand\";v=\"99\", \"Chromium\";v=\"87\"")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36");

        //Set media type based on request type
        switch (type) {
            case Json:
                requestBuilder.addHeader("Accept", "application/json, text/plain, */*");
                requestBuilder.addHeader("Content-Type", "application/json; charset=utf-8");
                break;
            case HtmlGet:
                requestBuilder.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
            case HtmlPost:
                requestBuilder.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
                requestBuilder.addHeader("Content-Type", "application/x-www-form-urlencoded");
                break;
        }

    }

    protected void correctTimeInRecentData(RecentData recentData) {

        boolean timezoneMissing = false;
        String offsetString = null;

        if (recentData.sMedicalDeviceTime != null && !recentData.sMedicalDeviceTime.isEmpty() && recentData.lastMedicalDeviceDataUpdateServerTime > 1) {

            //MedicalDeviceTime string has no timezone information
            if (parseDateString(recentData.sMedicalDeviceTime) == null) {

                timezoneMissing = true;

                //offset = this.getZonedDate(recentData.lastSG.datetime).getOffset();
                offsetString = this.getZoneOffset(recentData.lastSG.datetime);

                //Build correct dates with timezone
                recentData.sMedicalDeviceTime = recentData.sMedicalDeviceTime + offsetString;
                recentData.medicalDeviceTimeAsString = recentData.medicalDeviceTimeAsString + offsetString;
                recentData.sLastSensorTime = recentData.sLastSensorTime + offsetString;
                recentData.lastSensorTSAsString = recentData.lastSensorTSAsString + offsetString;

            } else {
                timezoneMissing = false;
            }

            //Parse dates
            recentData.dMedicalDeviceTime = parseDateString(recentData.sMedicalDeviceTime);
            recentData.medicalDeviceTimeAsDate = parseDateString(recentData.medicalDeviceTimeAsString);
            recentData.dLastSensorTime = parseDateString(recentData.sLastSensorTime);
            recentData.lastSensorTSAsDate = parseDateString(recentData.lastSensorTSAsString);

            //Sensor
            if (recentData.sgs != null) {
                for (SensorGlucose sg : recentData.sgs) {
                    sg.datetimeAsDate = parseDateString(sg.datetime);
                }
            }

            //Timezone was present => check if time needs correction
            if (!timezoneMissing) {

                //Calc time diff between event time and actual local time
                int diffInHour = (int) Math.round(((recentData.lastMedicalDeviceDataUpdateServerTime - recentData.dMedicalDeviceTime.getTime()) / 3600000D));

                //Correct times if server <> device > 26 mins => possibly different time zones
                if (diffInHour != 0 && diffInHour < 26) {


                    recentData.medicalDeviceTimeAsDate = shiftDateByHours(recentData.medicalDeviceTimeAsDate, diffInHour);
                    recentData.dMedicalDeviceTime = shiftDateByHours(recentData.dMedicalDeviceTime, diffInHour);
                    recentData.lastConduitDateTime = shiftDateByHours(recentData.lastConduitDateTime, diffInHour);
                    recentData.lastSensorTSAsDate = shiftDateByHours(recentData.lastSensorTSAsDate, diffInHour);
                    recentData.dLastSensorTime = shiftDateByHours(recentData.dLastSensorTime, diffInHour);
                    //Sensor
                    if (recentData.sgs != null) {
                        for (SensorGlucose sg : recentData.sgs) {
                            sg.datetimeAsDate = shiftDateByHours(sg.datetimeAsDate, diffInHour);
                        }
                    }
                    //Markers
                    if (recentData.markers != null) {
                        for (Marker marker : recentData.markers) {
                            marker.dateTime = shiftDateByHours(marker.dateTime, diffInHour);
                        }
                    }
                    //Notifications
                    if (recentData.notificationHistory != null) {
                        if (recentData.notificationHistory.clearedNotifications != null) {
                            for (ClearedNotification notification : recentData.notificationHistory.clearedNotifications) {
                                notification.dateTime = shiftDateByHours(notification.dateTime, diffInHour);
                                notification.triggeredDateTime = shiftDateByHours(notification.triggeredDateTime, diffInHour);
                            }
                        }
                        if (recentData.notificationHistory.activeNotifications != null) {
                            for (ActiveNotification notification : recentData.notificationHistory.activeNotifications) {
                                notification.dateTime = shiftDateByHours(notification.dateTime, diffInHour);
                            }
                        }
                    }
                }
            }

        }

        //Set dateTime of Markers using index if dateTime is missing (Guardian Connect)
        if (recentData.dLastSensorTime != null && recentData.markers != null) {
            for (Marker marker : recentData.markers) {
                if (marker != null && marker.dateTime == null) {
                    try {
                        marker.dateTime = calcTimeByIndex(recentData.dLastSensorTime, marker.index, true);
                    } catch (Exception ex) {
                        continue;
                    }
                }
            }
        }

    }

    protected String getZoneOffset(String dateString) {
        Matcher offsetDataMatcher = Pattern.compile(("(.*)([\\+|-].*)")).matcher(dateString);
        if (offsetDataMatcher.find())
            return offsetDataMatcher.group(2);
        else
            return null;
    }

    protected Date parseDateString(String dateString) {
        for (SimpleDateFormat zonedFormat : ZONED_DATE_FORMATS) {
            try {
                return zonedFormat.parse(dateString);
            } catch (Exception ex) {
            }
        }
        return null;
    }

    protected Date shiftDateByHours(Date date, int hours) {
        if (date != null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            calendar.add(Calendar.HOUR_OF_DAY, hours);
            return calendar.getTime();
        } else {
            return null;
        }
    }

    //Calculate DateTime using graph index (1 index = 5 minute)
    protected static Date calcTimeByIndex(Date lastSensorTime, int index, boolean round) {
        if (lastSensorTime == null)
            return null;
        else if (round)
            //round to 10 minutes
            return new Date((Math.round((calcTimeByIndex(lastSensorTime, index, false).getTime()) / 600_000D) * 600_000L));
        else
            return new Date((lastSensorTime.getTime() - ((287 - index) * 300_000L)));
    }

}
