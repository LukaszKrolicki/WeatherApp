package eu.pl.snk.senseibunny.weatherapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object Const{
    fun isNetworkAvailable(context: Context): Boolean { //check if network is available

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork?: return false
            val activeNetworkInfo = connectivityManager.getNetworkCapabilities(network)?: return false
            return when{
                activeNetworkInfo.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetworkInfo.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetworkInfo.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        }
        else{
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo!= null && networkInfo.isConnectedOrConnecting
        }
    }

    const val PREFERENCES_NAME = "WeatherAppPreferences"
    const val WEATHER_RESPONSE_DATA = "weather_response_data"
}