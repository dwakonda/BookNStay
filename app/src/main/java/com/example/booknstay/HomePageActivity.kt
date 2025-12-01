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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Payment
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class HomePageActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Home tab state
    private val hotelsState = mutableStateOf<List<HotelItem>>(emptyList())
    private val isLoadingState = mutableStateOf(true)

    // Bottom nav + selection
    private val currentTabState = mutableStateOf(BottomTab.HOME)
    private val selectedHotelState = mutableStateOf<HotelItem?>(null)

    // Booking history state
    private val bookingsState = mutableStateOf<List<BookingItem>>(emptyList())
    private var bookingsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        listenToPopularHotels()
        listenToBookings()

        enableEdgeToEdge()
        setContent {
            BookNStayTheme {

                val hotels by hotelsState
                val isLoading by isLoadingState
                val currentTab by currentTabState
                val selectedHotel by selectedHotelState
                val bookings by bookingsState

                val gradient = Brush.verticalGradient(
                    listOf(
                        Color(0xFF3E8E7E),
                        Color(0xFF77BFA3),
                        Color(0xFFD4ECDD)
                    )
                )

                Scaffold(
                    bottomBar = {
                        BottomNavBar(
                            currentTab = currentTab,
                            onTabSelected = { tab -> currentTabState.value = tab }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(gradient)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {

                        when (currentTab) {
                            BottomTab.HOME -> HomeTabScreen(
                                hotels = hotels,
                                isLoading = isLoading,
                                onLogout = {
                                    auth.signOut()
                                    startActivity(
                                        Intent(
                                            this@HomePageActivity,
                                            LoginPageActivity::class.java
                                        )
                                    )
                                    finish()
                                },
                                onHotelClick = { hotel ->
                                    selectedHotelState.value = hotel
                                    currentTabState.value = BottomTab.PAYMENT
                                }
                            )

                            BottomTab.HISTORY -> BookingHistoryScreen(bookings = bookings)

                            BottomTab.PAYMENT -> PaymentScreen(
                                selectedHotel = selectedHotel,
                                onBackToHome = {
                                    currentTabState.value = BottomTab.HOME
                                },
                                onConfirmBooking = { hotel, checkIn, checkOut, guests, method ->
                                    createBooking(
                                        hotel = hotel,
                                        checkIn = checkIn,
                                        checkOut = checkOut,
                                        guests = guests,
                                        paymentMethod = method,
                                        onSuccess = {
                                            Toast.makeText(
                                                this@HomePageActivity,
                                                "Booking confirmed!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            currentTabState.value = BottomTab.HISTORY
                                        },
                                        onError = { msg ->
                                            Toast.makeText(
                                                this@HomePageActivity,
                                                "Error: $msg",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun listenToPopularHotels() {
        db.collection("hotels")
            // remove orderBy if you don't have "rating" field
            .orderBy("rating", Query.Direction.DESCENDING)
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

    private fun listenToBookings() {
        val userId = auth.currentUser?.uid ?: return

        bookingsListener?.remove()
        bookingsListener = db.collection("bookings")
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val list = snapshot?.documents?.map { doc ->
                    BookingItem(
                        id = doc.id,
                        hotelName = doc.getString("hotelName") ?: "",
                        city = doc.getString("city") ?: "",
                        checkIn = doc.getString("checkIn") ?: "",
                        checkOut = doc.getString("checkOut") ?: "",
                        guests = doc.getString("guests") ?: "",
                        price = doc.getString("price") ?: "",
                        paymentMethod = doc.getString("paymentMethod") ?: ""
                    )
                } ?: emptyList()

                bookingsState.value = list
            }
    }

    private fun createBooking(
        hotel: HotelItem,
        checkIn: String,
        checkOut: String,
        guests: String,
        paymentMethod: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onError("User not logged in")
            return
        }

        val booking = hashMapOf(
            "userId" to userId,
            "hotelId" to hotel.id,
            "hotelName" to hotel.name,
            "city" to hotel.city,
            "checkIn" to checkIn,
            "checkOut" to checkOut,
            "guests" to guests,
            "price" to hotel.price,
            "paymentMethod" to paymentMethod,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("bookings")
            .add(booking)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e ->
                onError(e.message ?: "Unknown error")
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        bookingsListener?.remove()
    }
}

// ----------------- BOTTOM NAV -----------------

enum class BottomTab {
    HOME, HISTORY, PAYMENT
}

@Composable
fun BottomNavBar(
    currentTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentTab == BottomTab.HOME,
            onClick = { onTabSelected(BottomTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = currentTab == BottomTab.HISTORY,
            onClick = { onTabSelected(BottomTab.HISTORY) },
            icon = { Icon(Icons.Default.History, contentDescription = "History") },
            label = { Text("Bookings") }
        )
        NavigationBarItem(
            selected = currentTab == BottomTab.PAYMENT,
            onClick = { onTabSelected(BottomTab.PAYMENT) },
            icon = { Icon(Icons.Default.Payment, contentDescription = "Payment") },
            label = { Text("Payment") }
        )
    }
}

// ----------------- HOME TAB -----------------

@Composable
fun HomeTabScreen(
    hotels: List<HotelItem>,
    isLoading: Boolean,
    onLogout: () -> Unit,
    onHotelClick: (HotelItem) -> Unit
) {
    val ctx = LocalContext.current

    // search state
    var destination by remember { mutableStateOf("") }
    var checkIn by remember { mutableStateOf("") }
    var checkOut by remember { mutableStateOf("") }
    var guests by remember { mutableStateOf("") }

    var filteredHotels by remember(hotels) { mutableStateOf(hotels) }
    val isSearching = destination.isNotBlank()
    val listToShow = if (isSearching) filteredHotels else hotels

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
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Find your perfect stay",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp
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

        Spacer(Modifier.height(12.dp))

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

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = checkIn,
                onValueChange = { checkIn = it },
                label = { Text("Check-in date") },
                placeholder = { Text("dd/mm/yyyy") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = checkOut,
                onValueChange = { checkOut = it },
                label = { Text("Check-out date") },
                placeholder = { Text("dd/mm/yyyy") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = guests,
                onValueChange = { guests = it },
                label = { Text("Guests") },
                placeholder = { Text("2 adults, 1 room") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    if (destination.isBlank()) {
                        Toast.makeText(
                            ctx,
                            "Please enter a destination",
                            Toast.LENGTH_SHORT
                        ).show()
                        filteredHotels = hotels
                    } else {
                        filteredHotels = hotels.filter {
                            it.city.contains(destination, ignoreCase = true) ||
                                    it.name.contains(destination, ignoreCase = true)
                        }
                        if (filteredHotels.isEmpty()) {
                            Toast.makeText(
                                ctx,
                                "No stays found for $destination",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                ctx,
                                "Found ${filteredHotels.size} stays in/near $destination",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Search", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (isSearching) "Search results" else "Popular stays",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        )

        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (listToShow.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopStart
            ) {
                Text(
                    text = if (isSearching)
                        "No stays found for \"$destination\"."
                    else
                        "No popular stays available.",
                    color = Color.White
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(listToShow) { hotel ->
                    HotelCard(
                        hotel = hotel,
                        onClick = { onHotelClick(hotel) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ----------------- PAYMENT TAB -----------------

@Composable
fun PaymentScreen(
    selectedHotel: HotelItem?,
    onBackToHome: () -> Unit,
    onConfirmBooking: (HotelItem, String, String, String, String) -> Unit
) {
    val ctx = LocalContext.current

    if (selectedHotel == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No hotel selected.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = onBackToHome) {
                Text("Back to home")
            }
        }
        return
    }

    var checkIn by remember { mutableStateOf("") }
    var checkOut by remember { mutableStateOf("") }
    var guests by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Card") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
        Text(
            text = "Payment",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = selectedHotel.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${selectedHotel.city} • ${selectedHotel.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = selectedHotel.price,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = checkIn,
            onValueChange = { checkIn = it },
            label = { Text("Check-in date") },
            placeholder = { Text("dd/mm/yyyy") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = checkOut,
            onValueChange = { checkOut = it },
            label = { Text("Check-out date") },
            placeholder = { Text("dd/mm/yyyy") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = guests,
            onValueChange = { guests = it },
            label = { Text("Guests") },
            placeholder = { Text("2 adults, 1 room") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Payment method",
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(6.dp))

        Row {
            FilterChip(
                selected = paymentMethod == "Card",
                onClick = { paymentMethod = "Card" },
                label = { Text("Card") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = paymentMethod == "Cash",
                onClick = { paymentMethod = "Cash" },
                label = { Text("Cash") }
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                if (checkIn.isBlank() || checkOut.isBlank() || guests.isBlank()) {
                    Toast.makeText(
                        ctx,
                        "Please fill all booking details",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onConfirmBooking(
                        selectedHotel,
                        checkIn,
                        checkOut,
                        guests,
                        paymentMethod
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Pay now", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ----------------- BOOKING HISTORY TAB -----------------

data class BookingItem(
    val id: String = "",
    val hotelName: String = "",
    val city: String = "",
    val checkIn: String = "",
    val checkOut: String = "",
    val guests: String = "",
    val price: String = "",
    val paymentMethod: String = ""
)

@Composable
fun BookingHistoryScreen(
    bookings: List<BookingItem>
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Booking history",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        if (bookings.isEmpty()) {
            Text(
                text = "No bookings yet.",
                color = Color.White
            )
        } else {
            LazyColumn {
                items(bookings) { booking ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text(
                                text = booking.hotelName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${booking.city} • ${booking.checkIn} - ${booking.checkOut}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Guests: ${booking.guests}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${booking.price} • ${booking.paymentMethod}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------- SHARED MODELS -----------------

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
