package eu.pl.snk.senseibunny.weatherapp

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import eu.pl.snk.senseibunny.weatherapp.databinding.ActivityMainBinding
import eu.pl.snk.senseibunny.weatherapp.models.WeatherResponse
import eu.pl.snk.senseibunny.weatherapp.network.WeatherService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding ?=null

    private lateinit var LocationProvider: FusedLocationProviderClient

    public var mLatitude: Double = 0.0

    public var mLongitude: Double = 0.0

    private lateinit var progressDialog: Dialog

    private var interval = 1000000


    private lateinit var sharedPreferences: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        LocationProvider = LocationServices.getFusedLocationProviderClient(this)

        if (isLocationEnabled().equals(false)) { //if location is disabled go to sett
            Toast.makeText(this, "Location is not enabled", Toast.LENGTH_LONG).show()
            val Intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(Intent)

        } else { //else we want to get permissions
            Dexter.withActivity(this).withPermissions(
                android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    if (report.areAllPermissionsGranted()) {
                        Toast.makeText(
                            this@MainActivity,
                            "Location Enabled",
                            Toast.LENGTH_LONG
                        ).show()
                        requestNewLocationData()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: MutableList<PermissionRequest>?,
                    p1: PermissionToken?
                ) {
                    showRationaleDialogForPermissions()
                }
            }).onSameThread().check()


        }

        sharedPreferences = getSharedPreferences(Const.PREFERENCES_NAME, Context.MODE_PRIVATE)

        setupUI()

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.refresh -> {
                interval = 1000
                requestNewLocationData()
                Handler().postDelayed({
                    interval=1000000
                    requestNewLocationData()
                }, 5000)

                true
            } else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI() {

        val weatherResponseJsonString=sharedPreferences.getString(Const.WEATHER_RESPONSE_DATA,null)

        if(weatherResponseJsonString!=null){
            val weatherResponse=Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)

            binding?.weatherText1?.text= weatherResponse.weather[0].main
            binding?.weatherText2?.text= weatherResponse.weather[0].description
            binding?.HumidityText1?.text= weatherResponse.main.temp.toString()+ " °C"
            binding?.HumidityText2?.text=weatherResponse.main.humidity.toString()
            binding?.tempText1?.text=weatherResponse.main.temp_min.toString() + " min"
            binding?.tempText2?.text=weatherResponse.main.temp_max.toString() + " max"

            val speed = weatherResponse.wind.speed*1.6
            binding?.windText1?.text=speed.toString().take(5)

            binding?.LocationText1?.text=weatherResponse.name
            binding?.LocationText2?.text=weatherResponse.sys.country

            binding?.sunsetText1?.text=unixTime(weatherResponse.sys.sunset)
            binding?.SunriseText1?.text=unixTime(weatherResponse.sys.sunrise)


            try{
                Log.d("TAG", weatherResponse.weather[0].icon.toString())
                val imageUrl = "https://openweathermap.org/img/wn/${weatherResponse.weather[0].icon}@2x.png"
                val imageView = findViewById<ImageView>(R.id.weatherIMG)

                loadImageFromUrl(this, imageUrl, imageView)
            }
            catch (e:Exception){
                e.printStackTrace()
            }
        }

    }

    fun loadImageFromUrl(context: Context, imageUrl: String, imageView: ImageView) {
        val requestOptions = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerCrop()

        Glide.with(context)
            .load(imageUrl)
            .apply(requestOptions)
            .into(imageView)
    }

    private fun unixTime(timex: Long): String?{
        val date = Date(timex*1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    private fun getLocationWeatherDetails(){
        if(Const.isNetworkAvailable(this)){

            showProgressDialog()

            val retrofit: Retrofit = Retrofit.Builder().baseUrl(getString(R.string.BASE_URL)) //pass the base url
                .addConverterFactory(GsonConverterFactory.create()).build()

            val service: WeatherService = retrofit.create(WeatherService::class.java) //We make URL complete

            val listCall: Call<WeatherResponse> = service.getWeather(mLatitude, mLongitude,getString(R.string.API_KEY), getString(R.string.METRIC_UNIT)) //We make call to the server

            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) { //We get response from the server
                    hideProgressDialog()
                    if(response.isSuccessful){
                        val weatherList: WeatherResponse = response.body()!!
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = sharedPreferences.edit()
                        editor.putString(Const.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()
                        setupUI()
                        Log.d("TAG", "onResponse: ${weatherList.toString()}")
                    }
                    else{
                        val rc= response.code()
                        when(rc){
                            400 -> Log.e("TAG", "onResponse 400: ${response.errorBody()}")
                            404-> Log.e("TAG", "onResponse 400: ${response.errorBody()}")
                            else -> Log.e("TAG", "error")
                        }
                    }
                }
                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) { //We get error from the server
                    hideProgressDialog()
                    Log.e("TAG", "onFailure: ${t.message}")
                }
            })
        }
        else{
            Toast.makeText(this,"Network is not available", Toast.LENGTH_LONG).show()
        }
    }

    private fun isLocationEnabled(): Boolean { //asking if location is enabled
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager // getting location manager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showRationaleDialogForPermissions(){
        AlertDialog.Builder(this).setMessage("It looks like you didnt give permissions thats sad L").setPositiveButton("Go to Settings") { dialog, which ->
            try{
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            } catch (e:Exception){
                e.printStackTrace()
            }
        }.setNegativeButton("Cancel") { dialog, which ->
            dialog.dismiss()
        }.show()

    }


    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        val mLocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,
            interval.toLong()
        )
            .setWaitForAccurateLocation(false)
            .build()

        LocationProvider.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation = locationResult.lastLocation
            mLatitude=mLastLocation!!.latitude
            mLongitude=mLastLocation!!.longitude
            Log.d("LOG", "Latitude: " + mLatitude + "Longitude: " + mLongitude)
            getLocationWeatherDetails()

        }
    }

    private fun showProgressDialog(){
        progressDialog = Dialog(this)
        progressDialog.setContentView(R.layout.progress_dialog)
        progressDialog.show()
    }

    private fun hideProgressDialog(){
        progressDialog.dismiss()
    }


}