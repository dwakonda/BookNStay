package com.example.booknstay

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.booknstay.ui.theme.BookNStayTheme
import com.google.firebase.auth.FirebaseAuth

class HomePageActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        enableEdgeToEdge()
        setContent {
            BookNStayTheme {
                HomePageScreen(
                    onLogout = {
                        auth.signOut()
                        startActivity(Intent(this, LoginPageActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun HomePageScreen(
    onLogout: () -> Unit
) {
    val ctx = LocalContext.current

    var destination by remember { mutableStateOf("") }
    var checkIn by remember { mutableStateOf("") }
    var checkOut by remember { mutableStateOf("") }
    var guests by remember { mutableStateOf("") }

    val gradient = Brush.verticalGradient(
        listOf(
            Color(0xFF3E8E7E),
            Color(0xFF77BFA3),
            Color(0xFFD4ECDD)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BookNStay",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Find your perfect stay",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp
                    )
                }

                IconButton(onClick = { onLogout() }) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Logout",
                        tint = Color.White
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Search card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color.White.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = "Search hotels",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Destination") },
                    placeholder = { Text("City, area or hotel") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = checkIn,
                    onValueChange = { checkIn = it },
                    label = { Text("Check-in date") },
                    placeholder = { Text("e.g. 10 Apr 2025") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = checkOut,
                    onValueChange = { checkOut = it },
                    label = { Text("Check-out date") },
                    placeholder = { Text("e.g. 12 Apr 2025") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = guests,
                    onValueChange = { guests = it },
                    label = { Text("Guests") },
                    placeholder = { Text("2 adults, 1 room") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (destination.isBlank()) {
                            Toast.makeText(
                                ctx,
                                "Please enter a destination",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                ctx,
                                "Searching stays in $destination...",
                                Toast.LENGTH_SHORT
                            ).show()
                            // later: navigate to SearchResultsActivity with these values
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Search", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Popular stays",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )

            Spacer(Modifier.height(10.dp))

            // Simple hotel list
            val sampleHotels = remember {
                listOf(
                    HotelItem("City Center Hotel", "London • 0.5 km from centre", "£120 / night"),
                    HotelItem("Sea View Resort", "Brighton • Near the beach", "£95 / night"),
                    HotelItem("Business Inn", "Manchester • Close to station", "£110 / night")
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(sampleHotels) { hotel ->
                    HotelCard(hotel = hotel, onClick = {
                        // later: open HotelDetailsActivity
                        Toast
                            .makeText(ctx, "Opening ${hotel.name}", Toast.LENGTH_SHORT)
                            .show()
                    })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

data class HotelItem(
    val name: String,
    val location: String,
    val price: String
)

@Composable
fun HotelCard(
    hotel: HotelItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = hotel.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = hotel.location,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = hotel.price,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
