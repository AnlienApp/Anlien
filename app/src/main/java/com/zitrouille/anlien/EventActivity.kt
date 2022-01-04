package com.zitrouille.anlien

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView


class EventActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event)

        val bottomMenu = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val chatView: View = bottomMenu.findViewById(R.id.nav_chat)
        chatView.performClick()

        /**
         * Go back to the homepage activity
         */
        val backView: View = bottomMenu.findViewById(R.id.nav_back)
        backView.setOnClickListener {
            finish()
        }
    }
}