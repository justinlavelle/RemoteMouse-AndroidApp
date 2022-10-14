package com.example.remotemouse

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.example.remotemouse.databinding.ActivityMainBinding
import com.example.remotemouse.databinding.IpDialogLayoutBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.Socket


class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var socketThread: Thread

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        getIp()
    }

    /**
     * METHOD TO GET IP FROM USER
     */
    private fun getIp() {
        //custom dialog layout
        val dialogBinding = DataBindingUtil.inflate<IpDialogLayoutBinding>(
            LayoutInflater.from(this), R.layout.ip_dialog_layout, null, false
        )
        dialogBinding.ipEditText.setText(viewModel.ip)
        // Create an alert dialog to get ip from user
        MaterialAlertDialogBuilder(this).setTitle("Enter IP").setView(dialogBinding.root)
            .setPositiveButton("Connect") { _, _ ->
                val ip = dialogBinding.ipEditText.text.toString()
                viewModel.ip = ip

                //now start socket connection
                setTouchPad()
                setMouseButtons()
                connectToServer(ip)
            }.setNegativeButton("Exit") { _, _ ->
                finish()
            }.setCancelable(false).show()
    }

    /**
     * METHOD TO CONNECT WITH SERVER AND PUT ON A BACKGROUND THREAD
     */
    private fun connectToServer(ip: String) {
        socketThread = Thread {
            try {
                viewModel.socket = Socket(ip, 8080)
                viewModel.socket!!.tcpNoDelay = true

                //toasts can only be shown on main thread
                runOnUiThread {
                    Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show()
                }
                viewModel.writer =
                    BufferedWriter(OutputStreamWriter(viewModel.socket!!.getOutputStream()))

                viewModel.event = "move"

                //start infinite loop to send data to server
                viewModel.sendData()
            } catch (e: Exception) {

                //toasts can only be shown on main thread
                runOnUiThread {
                    Toast.makeText(this, "Error Connecting to Server", Toast.LENGTH_SHORT).show()
                    getIp()
                    return@runOnUiThread
                }
                e.printStackTrace()
            }
        }

        //start background thread
        socketThread.start()
    }

    /**
     * METHOD TO SET TOUCHPAD LISTENERS
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchPad() {

        binding.touchPad.setOnTouchListener { _, event ->
            when (event.action) {
                //when user touches the screen
                MotionEvent.ACTION_DOWN -> {
                    //store initial coordinates of touch in x and y
                    viewModel.x = event.x.toInt()
                    viewModel.y = event.y.toInt()
                    viewModel.event = "down"

                    //also at this moment initiate left click
                    viewModel.click = true
                }
                //when user moves finger on screen
                MotionEvent.ACTION_MOVE -> {
                    //calculate change in x and y more precisely by dividing by 2
                    viewModel.dx = (event.x.toInt() - viewModel.x) / 2
                    viewModel.dy = (event.y.toInt() - viewModel.y) / 2

                    //update x and y
                    viewModel.x = event.x.toInt()
                    viewModel.y = event.y.toInt()
                    viewModel.event = "move"

                    //left click should not be initiated when user moves finger
                    viewModel.click = false
                }
                //when user lifts finger from screen
                MotionEvent.ACTION_UP -> {
                    viewModel.event = "up"
                    //left click fired when user lifts finger without moving it
                    if (viewModel.click) {
                        viewModel.sendLeftClick()

                        //left click should not be fired again
                        viewModel.click = false
                    }
                }
            }
            true
        }
    }

    /**
     * METHOD TO SET MOUSE BUTTON LISTENERS
     */
    private fun setMouseButtons() {
        binding.leftClick.setOnClickListener {
            viewModel.sendLeftClick()
        }
        binding.rightClick.setOnClickListener {
            viewModel.sendRightClick()
        }
    }

    /**
     * METHOD TO CLOSE SOCKET WHEN USER EXITS APP
     */
    override fun onStop() {
        super.onStop()
        viewModel.socket!!.close()
        socketThread.join()
    }

}