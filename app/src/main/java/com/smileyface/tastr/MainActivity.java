package com.smileyface.tastr;


import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Process;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.Manifest;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.smileyface.tastr.Utilities.firebaseHandler;

import java.util.ArrayList;

import static java.lang.String.valueOf;
public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,LocationListener {

    //Permission Variables
    private static final int PERMISSION_ACCESS_COARSE_LOCATION = 1;

    //Variables for requesting locations from Google.
    private static final long POLL_FREQ = 1;
    private static final long FASTEST_UPDATE_FREQ = 100 * 5;
    private LocationRequest mLocationRequest;
    private GoogleApiClient googleApiClient;

    // Location Variables
    String currentLat = null;
    String currentLong = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Setup buttons for the main menu
        Button tActivityButton = (Button) findViewById(R.id.touchViewButton);
        Button sActivityButton = (Button) findViewById(R.id.settingsViewButton);

        tActivityButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, TouchActivity.class));
            }
        });

        sActivityButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        // Check GPS permissions on the device and ask for them if they aren't already granted.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_ACCESS_COARSE_LOCATION);
        }

        // If permission is granted/already enabled then contact google services.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            //initialize the gps search parameters.
            mLocationRequest = LocationRequest.create();
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            mLocationRequest.setInterval(POLL_FREQ);
            mLocationRequest.setFastestInterval(FASTEST_UPDATE_FREQ);

            //checks if google services are available and connects to their location API
            if (servicesAvailable()) {
                googleApiClient = new GoogleApiClient.Builder(this)
                        .addApi(LocationServices.API)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .build();
                googleApiClient.connect();
            } else {
                System.err.println("Google Play Services Unavailable");
            }
        }
        yelpLoader load = new yelpLoader();
        load.execute();
    }//On Create

    // This is an example of what we can do if the user declines location services.
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_ACCESS_COARSE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    break;
                } else {
                    Toast.makeText(this, "Need your location!", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }


    //If we can't get GPS location from google this is where we should prompt the user for a zip code or something instead of using GPS to find restaurants.
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(YelpActivity.class.getSimpleName(), "Can't connect to Google Play Services!");
        System.err.println("CONNECTION TO GOOGLE FAILED");
    }

    //Start requesting location information from Google if connection is successful.
    @Override
    public void onConnected(Bundle dataBundle) {
        System.err.println("SUCCESSFULLY CONNECTED TO GOOGLE");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_ACCESS_COARSE_LOCATION);
        }

        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, mLocationRequest,this);
    }

    // One of two places that Longitude and Latitude might be set, somehwat redundant but if you lose GPS location and had it earlier when the app was running this
    // will allow restaurants to populate based on your last known location instead of the default location in YelpAPI.Launcher
    private Location bestLastKnownLocation(float minAccuracy, long minTime) {
        System.err.println("GOT TO METHOD: BEST LAST KNOWN LOCATION");
        Location bestResult = null;
        float bestAccuracy = Float.MAX_VALUE;
        long bestTime = Long.MIN_VALUE;

        // Get the best most recent location currently available
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_ACCESS_COARSE_LOCATION);
        }
        // checks for GPS permission again, if it still isn't granted then the app will close. Change the line that calls System.exit(0) later on in development to something less abrasive.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            System.exit(0);
        }

        //ask google for gps information
        Location mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        //set new gps values
        currentLat = valueOf(mCurrentLocation.getLatitude());
        currentLong = valueOf(mCurrentLocation.getLongitude());

        //print new values
        System.out.println(currentLong);
        System.out.println(currentLat);

        //checks to make sure google didn't give us less accurate gps location than the last location if gave
        if (mCurrentLocation.getLongitude() != 0.0) {
            float accuracy = mCurrentLocation.getAccuracy();
            long time = mCurrentLocation.getTime();

            if (accuracy < bestAccuracy) {
                bestResult = mCurrentLocation;
                bestAccuracy = accuracy;
                bestTime = time;
            }
        }

        // Return best reading or null
        if (bestAccuracy > minAccuracy || bestTime < minTime) {
            return null;
        }
        else {
            return bestResult;
        }
    }

    //Checks to make sure Google Play isn't offline.
    private boolean servicesAvailable() {
        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
        int result = googleAPI.isGooglePlayServicesAvailable(this);
        if (result != ConnectionResult.SUCCESS) {

            return false;
        }

        return true;
    }

    // If the connection from google stops coming in do stuff.
    @Override
    public void onConnectionSuspended(int i) {

    }

    // Changes Variables Latitude and Longitude when a new location is found.
    public void onLocationChanged(Location location) {

        System.err.println("LOCATION RECEIVED!");

        if (location != null) {

            currentLong = valueOf(location.getLongitude());
            currentLat = valueOf(location.getLatitude());

            System.out.println("Longitude:" + currentLong);
            System.out.println("Latitude:" + currentLat);
        }
    }

    // Yelp Loader
    // Async task for contacting the Yelp API. Important to preform the operation off the main thread, or the app will crash and give internet connection on main thread exception.
    private class yelpLoader extends AsyncTask<String, Void, String> {


        int numberOfBusinesses = 0;
        YelpAPI API = new YelpAPI();
        ArrayList<String> businessIDs = new ArrayList<>();// important to use an Arraylist for the buisness ID values so its easier to iterate through them and retrieve them based on index in order of closest to furthest. (Or in whatever way we choose to sort them)
        ArrayList<String> ratings = new ArrayList<String>();
        ArrayList<String> cities = new ArrayList<String>();
        ArrayList<String> states = new ArrayList<String>();
        ArrayList<String> addresses = new ArrayList<String>();
        ArrayList<String> categories = new ArrayList<String>();
        ArrayList<String> phones = new ArrayList<String>();
        ArrayList<String> restaurants = new ArrayList<String>();


        // Everything you want to happen OUTSIDE of the GUI thread.
            protected String doInBackground(String... params) {


                // While loop will prevent API calls until gps location is found. We should eventually implement zip searching as an alternative.
                while(currentLat == null){

                }
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            if(currentLat != null && currentLong !=null) {
                API.setLocation(currentLat,currentLong);

            }//if

            // Starts the API, which will automatically contact yelp and populate the business information.
            API.run();

            // Checks how many Businesses yelp found
            numberOfBusinesses = API.getNumberOfBusinessIDS();

            // Makes a copy of the array of Business ID's found in the most recent Yelp API query. Resets the array list to avoid duplicate data.
            // Also clears the previous results if this is the second time hitting the button.
            businessIDs.clear();
            businessIDs = API.getBusinessIDList();
                ratings = API.getRatingList();
                cities = API.getCityList();
                states = API.getStateList();
                addresses = API.getAddressList();
                categories = API.getCategoryList();
                phones = API.getPhoneList();
                restaurants = API.getNameList();


                for (int i = 0; i < numberOfBusinesses; i++) {
                    firebaseHandler firebase = new firebaseHandler("Tastr Items");
                    if(firebase.searchForYelpID(businessIDs.get(i))){

                    }
                    else{
                       // Geocoder gcd = new Geocoder(MainActivity.this, Locale.getDefault());
                       // double lat = Double.parseDouble(currentLat);
                       // double longi = Double.parseDouble(currentLong);
                        //List<Address> addresses = null;
                       // try {
                            //addresses = gcd.getFromLocation(lat,longi,1);
                       // } catch (IOException e) {
                        //    e.printStackTrace();
                        //}
                        //if(addresses.size()>0){
                           // firebase.setCity(addresses.get(0).getLocality());
                            //firebase.setState(addresses.get(0).getAdminArea());
                        //}
                        TastrItem newItem = new TastrItem();
                        //firebase.setCity("Anchorage");
                        //firebase.setState("Alaska");
                        firebase.setYelpID(businessIDs.get(i));
                        newItem.setRating(ratings.get(i));
                        firebase.setCity(cities.get(i));
                        firebase.setState(states.get(i));
                        newItem.setRestaurant(restaurants.get(i));
                        newItem.setCategories(categories.get(i));
                        newItem.setPhone(phones.get(i));
                        newItem.setAddress(addresses.get(i));
                        firebase.writeTastrToDatabase(newItem);
                    }
                }//for

            return null;
        }//doInBackground

        // Everything you want to happen AFTER the doInBackground function is executed. Use this method to make changes to the GUI. IE listing all of the businesses found.
        @Override
        protected void onPostExecute(String result) {

            //error checking
            if (numberOfBusinesses <= 0) {

            }//if

            // if yelp finds any businesses then do stuff
            else {

                // This will clear the Business results from view technically, however the user won't notice a change unless the results change on the next API Call.
                // This prevents duplicates from populating on the list as businesses are added. Feel free to comment the line out and test the program if you are confused about what I mean.


                int count = 1;// for some reason i returns a two digit number and it screws up the counter as a string, so I just made a new variable for clarity.

                // This populates the list of business ID's one at a time on a new line.
                for (int i = 0; i < numberOfBusinesses; i++) {
                    //listBusinesses("\n" + count + "->" + businessIDs.get(i));
                    count++;

                }//for

            }//else

        }//On Post Execute

    }//Yelp Loader Class

}//MainActivity Class
