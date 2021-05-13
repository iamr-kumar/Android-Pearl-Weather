package com.example.pearlweather

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.example.pearlweather.databinding.ActivityMainBinding
import com.example.pearlweather.models.WeatherResponse
import com.example.pearlweather.network.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.jar.Manifest

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mProgressBarDialog: Dialog? = null

    private lateinit var sharedPreferences: SharedPreferences

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupUI()

//        showCustomProgressDialog()

        if(!isLocationEnabled()) {
            Toast.makeText(this,
                "Your location provider is not turned on. Please turn it on",
                Toast.LENGTH_SHORT)
                .show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this)
                .withPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION)
                .withListener(object : MultiplePermissionsListener {

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        Log.i("DENIED", "Permissions Denied!")
                        showRationaleDialogForPermissions()
                    }

                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                       if(report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                       }

                        if(report!!.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(this@MainActivity,
                                "You have denied one or more permissions for this app to work. Please accept the permissions",
                                 Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }).onSameThread().check()
        }

        

    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        showCustomProgressDialog()

        val mLocationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 1000
            numUpdates = 1

        }

        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.getMainLooper())
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            val longitude = mLastLocation.longitude

            Log.i("latitude", "$latitude")
            Log.i("longitude", "$longitude")

//            Toast.makeText(this@MainActivity, "Latitude $latitude and Longitude $longitude", Toast.LENGTH_LONG).show()

            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showRationaleDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off one or more permissions required for this app to run. Please turn them on")
            .setPositiveButton("GO TO SETTINGS") { _,_ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }

            }
            .setNegativeButton("CANCEL") {
                dialog, _ ->
                    dialog.dismiss()
            }.show()

    }

    private fun getLocationWeatherDetails(lat: Double, lon: Double) {

        if(Constants.isNetworkAvailable(this)) {
//            Toast.makeText(this, "You are connected to the internet", Toast.LENGTH_SHORT).show()
            val retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService = retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(lat, lon, Constants.METRIC_UNIT, Constants.APP_ID)



            listCall.enqueue(object: Callback<WeatherResponse>{
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    hideProgressDialog()
                    if (response.isSuccessful) {
                        val weatherList: WeatherResponse? = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList!!)
                        val editor = sharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()

                    } else {
                        val rc = response.code()
                        when(rc) {
                            400 -> {
                                Log.e("Error 400", "Bad connection!")
                            }
                            404 -> {
                                Log.e("Error 404", "Not found!")
                            }
                            else -> {
                                Log.e("Error", "Some error occured!")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    hideProgressDialog()
                    Log.e("Errror", t.message.toString())
                }

            })

        } else {
            Toast.makeText(this, "You are not connected to the internet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCustomProgressDialog() {
        mProgressBarDialog = Dialog(this)
        mProgressBarDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressBarDialog!!.show()


    }

    private fun hideProgressDialog() {
        if(mProgressBarDialog != null) {
            mProgressBarDialog!!.dismiss()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("SetTextI18n")
    private fun setupUI() {

        val weatherResponseJsonString = sharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if(!weatherResponseJsonString.isNullOrEmpty()) {
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            for(i in weatherList.weather.indices) {
                Log.i("Weather Name", weatherList.weather.toString())

                binding.tvMain.text = weatherList.weather[i].main
                binding.tvMainDescription.text = weatherList.weather[i].description
                binding.tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                binding.tvSunriseTime.text = unixTime(weatherList.sys.sunrise) + "AM"
                binding.tvSunsetTime.text = unixTime(weatherList.sys.sunset) + "PM"
                binding.tvHumidity.text = weatherList.main.humidity.toString() + "%"
                binding.tvCountry.text = weatherList.sys.country
                binding.tvWind.text = weatherList.wind.speed.toString()
                binding.tvMin.text = weatherList.main.tempMin.toString()
                binding.tvMax.text = weatherList.main.tempMax.toString()

                when(weatherList.weather[i].icon) {
                    "01d" -> binding.ivMain.setImageResource(R.drawable.sunny)
                    "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                    "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                    "13d" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                    "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "11n" -> binding.ivMain.setImageResource(R.drawable.rain)
                    "13n" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                }

            }
        } else {
            requestLocationData()
        }


    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.actionRefresh -> {
                requestLocationData()
                true
            } else -> {
                super.onOptionsItemSelected(item)
            }
        }

    }

    private fun getUnit(value: String): String {
        var returnVal = "°C"
        if("US" == value || "LR" == value || "MM" == value) {
            returnVal = "°F"
        }
        return returnVal
    }

    private fun unixTime(timex: Long): String {
        val date = Date(timex * 1000L)

        val sdf = SimpleDateFormat("hh:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }


}
