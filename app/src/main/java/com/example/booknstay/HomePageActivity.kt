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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class HomePageActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // State that Compose will observe
    private val hotelsState = mutableStateOf<List<HotelItem>>(emptyList())
    private val isLoadingState = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Start listening for popular hotels from Firestore
        listenToPopularHotels()

        enableEdgeToEdge()
        setContent {
            BookNStayTheme {
                val hotels by hotelsState
                val isLoading by isLoadingState

                HomePageScreen(
                    hotels = hotels,
                    isLoading = isLoading,
                    onLogout = {
                        auth.signOut()
                        startActivity(Intent(this, LoginPageActivity::class.java))
                        finish()
                    },
                    onSearch = { destination, checkIn, checkOut, guests ->
                        // For now just show a message.
                        // Later you can navigate to a SearchResultsActivity.
                        Toast.makeText(
                            this,
                            "Searching $destination ($checkIn - $checkOut, $guests)",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onHotelClick = { hotel ->
                        // For now just show which hotel was tapped.
                        // Later you can open a HotelDetailsActivity.
                        Toast.makeText(
                            this,
                            "Opening ${hotel.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }

    private fun listenToPopularHotels() {
        // Example: listen to "hotels" collection, ordered by rating (if you have it)
        db.collection("hotels")
            .orderBy("rating", Query.Direction.DESCENDING) // remove this line if you don't have "rating"
            .limit(10)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    isLoadingState.value = false
                    return@addSnapshotListener
                }

                val hotels = snapshot?.documents?.mapNotNull { doc ->
                    val name = doc.getString("name") ?: return@mapNotNull null
                    val location = doc.getString("location") ?: ""
                    val price = doc.getString("price") ?: ""
                    val city = doc.getString("city") ?: ""

                    HotelItem(
                        id = doc.id,
                        name = name,
                        location = location,
                        price = price,
                        city = city
                    )
                } ?: emptyList()

                hotelsState.value = hotels
                isLoadingState.value = false
            }
    }
}

@Composable
fun HomePageScreen(
    hotels: List<HotelItem>,
    isLoading: Boolean,
    onLogout: () -> Unit,
    onSearch: (String, String, String, String) -> Unit,
    onHotelClick: (HotelItem) -> Unit
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
                            onSearch(destination, checkIn, checkOut, guests)
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

            if (isLoading) {
                // Loading indicator while Firestore data is coming
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (hotels.isEmpty()) {
                // No hotels found
                Text(
                    text = "No popular stays available.",
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                // Show Firestore hotels
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(hotels) { hotel ->
                        HotelCard(
                            hotel = hotel,
                            onClick = { onHotelClick(hotel) }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

// --------- DATA + CARD UI ---------

data class HotelItem(
    val id: String = "",
    val name: String = "",
    val location: String = "",
    val price: String = "",
    val city: String = ""
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
                text = if (hotel.city.isNotBlank())
                    "${hotel.city} • ${hotel.location}"
                else
                    hotel.location,
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
