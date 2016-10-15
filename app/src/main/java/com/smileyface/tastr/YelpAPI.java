package com.smileyface.tastr;


        import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.scribe.builder.ServiceBuilder;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.oauth.OAuthService;

import java.util.ArrayList;


class YelpAPI implements Runnable{

    private static String API_HOST = "api.yelp.com";
    private static String DEFAULT_TERM = "restaurants near me";
    private static String DEFAULT_LOCATION = "Houston,Texas";
    // this CAN NOT be raised over 20. App will crash if it is.
    private static  int SEARCH_LIMIT = 20;
    private static String SEARCH_PATH = "/v2/search";
    private static String BUSINESS_PATH = "/v2/business";

    // Default values are null for error checking. If they are still null at the time of calling the Yelp API then the default location will be used in its place. (Much less accurate)
    private static String latitude = null;
    private static String longitude = null;

    /*
     * Update OAuth credentials below from the YelpAPI Developers API site:
     * http://www.yelp.com/developers/getting_started/api_access
     */
    private static final String CONSUMER_KEY = "cXrLIyuf1dhXyCeoKCRkHA";
    private static final String CONSUMER_SECRET = "nvdY0PsLigihJ6UoJb6HKycwvvE";
    private static final String TOKEN = "N8fy1BW4z_dyqruZXtrA709udr4pmJ6U";
    private static final String TOKEN_SECRET = "DrMxnxYFGYPSb7OD179A5FoGYnU";

    private OAuthService service;
    private Token accessToken;

    // Array lists for sorting the data and making it easily accessible.
    static ArrayList<String> BusinessIDList = new ArrayList<String>();
    static ArrayList<String> RatingList = new ArrayList<String>();
    static ArrayList<String> cityList = new ArrayList<String>();
    static ArrayList<String> stateList = new ArrayList<String>();
    static ArrayList<String> addressList = new ArrayList<String>();
    static ArrayList<String> categoryList = new ArrayList<String>();
    static ArrayList<String> phoneList = new ArrayList<String>();
    static ArrayList<String> nameList = new ArrayList<String>();

    //getters and setters for each array list.
    public static ArrayList<String> getPhoneList() {
        return phoneList;
    }

    public static void setPhoneList(ArrayList<String> phoneList) {
        YelpAPI.phoneList = phoneList;
    }

    public static ArrayList<String> getRatingList() {
        return RatingList;
    }

    public static void setRatingList(ArrayList<String> ratingList) {
        RatingList = ratingList;
    }

    public static ArrayList<String> getCityList() {
        return cityList;
    }

    public static void setCityList(ArrayList<String> cityList) {
        YelpAPI.cityList = cityList;
    }

    public static ArrayList<String> getStateList() {
        return stateList;
    }

    public static void setStateList(ArrayList<String> stateList) {
        YelpAPI.stateList = stateList;
    }

    public static ArrayList<String> getAddressList() {
        return addressList;
    }

    public static void setAddressList(ArrayList<String> addressList) {
        YelpAPI.addressList = addressList;
    }

    public static ArrayList<String> getCategoryList() {
        return categoryList;
    }

    public static void setCategoryList(ArrayList<String> categoryList) {
        YelpAPI.categoryList = categoryList;
    }

    public static ArrayList<String> getNameList() {
        return nameList;
    }

    public static void setNameList(ArrayList<String> nameList) {
        YelpAPI.nameList = nameList;
    }
    // end getters and setters

    // Methods for setting default search parameters, fairly self explanatory. Be sure to change these before calling API.run if you want to change the default variables.
    public void setSearchLimit(int limit){
        SEARCH_LIMIT = limit;
    }
    public void setLocation(String newlatitude, String newlongitude){ latitude = newlatitude; longitude = newlongitude;

    }
    public void setTerm(String term){
        DEFAULT_TERM = term;
    }

    //Method to grab specific business ID found in the yelp search.
    //This allows you to get only one, instead of populating an entire Array List. Not currently in use on the main activity.
    public String getBusinessID(int index){
        return BusinessIDList.get(index);
    }

    //Returns the number of Businesses found in Yelp, important for running the for loop in YelpActivity.Launcher
    public int getNumberOfBusinessIDS(){
        return BusinessIDList.size();
    }

    // Returns the entire list of business ID's in the form of an Array List. Use the method: "getBusinessID" to specify an index and grab one at a time.
    public ArrayList<String> getBusinessIDList(){
        return BusinessIDList;
    }

    public YelpAPI() {

        this.service = new ServiceBuilder().provider(TwoStepOAuth.class).apiKey(YelpAPI.CONSUMER_KEY).apiSecret(YelpAPI.CONSUMER_SECRET)
                .build();
        this.accessToken = new Token(YelpAPI.TOKEN, YelpAPI.TOKEN_SECRET);
    }

    /**
     * Creates and sends a request to the Search API by term and location.
     * <p>
     * See
     * <a href="http://www.yelp.com/developers/documentation/v2/search_api">YelpAPI
     * Search API V2</a> for more info.
     *
     * @param term
     *            <tt>String</tt> of the search term to be queried
     * @param location
     *            <tt>String</tt> of the location
     * @return <tt>String</tt> JSON Response
     */
    private String searchForBusinessesByLocation(String term, String location) {

        OAuthRequest request = createOAuthRequest(SEARCH_PATH);
        request.addQuerystringParameter("term", term);
        // checks for non null gps location, if it is null then it will use default location instead. (Can be a zip code "76207" or "City,State")
        if (latitude == null || longitude == null) {
            request.addQuerystringParameter("location", location);

        } else {
            request.addQuerystringParameter("ll", latitude + "," + longitude);
        }
        request.addQuerystringParameter("limit", String.valueOf(SEARCH_LIMIT));
        return sendRequestAndGetResponse(request);
    }

    /**
     * Creates and sends a request to the Business API by business ID.
     * <p>
     * See
     * <a href="http://www.yelp.com/developers/documentation/v2/business">YelpAPI
     * Business API V2</a> for more info.
     *
     * @param businessID
     *            <tt>String</tt> business ID of the requested business
     * @return <tt>String</tt> JSON Response
     */
    private String searchByBusinessId(String businessID) {

        OAuthRequest request = createOAuthRequest(BUSINESS_PATH + "/" + businessID);
        return sendRequestAndGetResponse(request);
    }

    /**
     * Creates and returns an {@link OAuthRequest} based on the API endpoint
     * specified.
     *
     * @param path
     *            API endpoint to be queried
     * @return <tt>OAuthRequest</tt>
     */
    private OAuthRequest createOAuthRequest(String path) {

        OAuthRequest request = new OAuthRequest(Verb.GET, "https://" + API_HOST + path);
        return request;
    }

    /**
     * Sends an {@link OAuthRequest} and returns the {@link Response} body.
     *
     * @param request
     *            {@link OAuthRequest} corresponding to the API request
     * @return <tt>String</tt> body of API response
     */
    private String sendRequestAndGetResponse(OAuthRequest request) {
        System.out.println("Querying " + request.getCompleteUrl() + " ...");
        this.service.signRequest(this.accessToken, request);

        Response response = request.send();
        return response.getBody();
    }

    /**
     * Queries the Search API based on the command line arguments and takes the
     * first result to query the Business API.
     *
     * @param yelpApi
     *            <tt>YelpAPI</tt> service instance
     * @param yelpApiCli
     *            <tt>YelpAPICLI</tt> command line arguments
     */
    private static void queryAPI(YelpAPI yelpApi, YelpAPICLI yelpApiCli) {

        String searchResponseJSON = yelpApi.searchForBusinessesByLocation(yelpApiCli.term, yelpApiCli.location);

        JSONParser parser = new JSONParser();
        JSONObject response = null;
        try {
            response = (JSONObject) parser.parse(searchResponseJSON);
        } catch (ParseException pe) {
            System.out.println("Error: could not parse JSON response:");
            System.out.println(searchResponseJSON);
            System.exit(1);
        }

        JSONArray businesses = (JSONArray) response.get("businesses");

        JSONObject firstBusiness = (JSONObject) businesses.get(0);


        //Populates the business ID Array List with all of the business ID's found in the yelp search.
        // Important to clear the list first so only the most recent results are displayed.
        BusinessIDList.clear();
        for(int i = 0; i < businesses.size(); i++){
            JSONObject temp = (JSONObject) businesses.get(i);
            JSONObject temp1 = (JSONObject) temp.get("location");
            BusinessIDList.add(temp.get("id").toString());
            RatingList.add(temp.get("rating").toString());
            cityList.add(temp1.get("city").toString());
            stateList.add(temp1.get("state_code").toString());
            phoneList.add(temp.get("phone").toString());
            categoryList.add(temp.get("categories").toString());
            addressList.add(temp1.get("address").toString().replaceAll("\\\\", ""));
            nameList.add(temp.get("name").toString());

        }



        String firstBusinessID = firstBusiness.get("id").toString();
        System.out.println(String.format("%s businesses found, querying business info for the top result \"%s\" ...",
                businesses.size(), firstBusinessID));

        // Select the first business and display business details
        String businessResponseJSON = yelpApi.searchByBusinessId(firstBusinessID);
        System.out.println(String.format("Result for business \"%s\" found:", firstBusinessID));
        System.out.println(businessResponseJSON);



    }


    static class YelpAPICLI {
        @Parameter(names = { "-q", "--term" }, description = "Search Query Term")
        public final String term = DEFAULT_TERM;

        @Parameter(names = {"-l", "--location"}, description = "Location to be Queried")
        public final String location = DEFAULT_LOCATION;
    }

    public void run(){


        YelpAPICLI yelpApiCli = new YelpAPICLI();
        new JCommander(yelpApiCli);

        YelpAPI yelpApi = new YelpAPI();
        queryAPI(yelpApi, yelpApiCli);}

}





