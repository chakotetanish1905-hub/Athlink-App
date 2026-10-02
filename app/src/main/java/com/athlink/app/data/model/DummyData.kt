package com.athlink.app.data.model

object DummyData {

    val coaches = listOf(
        Coach(
            uid = "coach1",
            name = "Rajiv Sharma",
            sport = "Cricket",
            bio = "Former state-level cricketer with 10+ years of coaching experience. Specializes in batting technique and mental conditioning.",
            experience = 10,
            rating = 4.8f,
            reviewCount = 120,
            hourlyRate = 800.0,
            location = "Mumbai, MH",
            specializations = listOf("Batting", "Fielding", "Mental Conditioning"),
            isAvailable = true
        ),
        Coach(
            uid = "coach2",
            name = "Priya Nair",
            sport = "Badminton",
            bio = "National-level badminton player turned coach. Helping players unlock their full potential on the court.",
            experience = 7,
            rating = 4.9f,
            reviewCount = 95,
            hourlyRate = 700.0,
            location = "Bangalore, KA",
            specializations = listOf("Singles", "Doubles", "Footwork"),
            isAvailable = true
        ),
        Coach(
            uid = "coach3",
            name = "Arjun Mehta",
            sport = "Football",
            bio = "UEFA-certified football coach. Worked with academies across India. Focus on tactical play and fitness.",
            experience = 12,
            rating = 4.7f,
            reviewCount = 210,
            hourlyRate = 1000.0,
            location = "Delhi, DL",
            specializations = listOf("Tactics", "Fitness", "Youth Development"),
            isAvailable = false
        ),
        Coach(
            uid = "coach4",
            name = "Sneha Patel",
            sport = "Swimming",
            bio = "Olympic trials participant. Passionate about competitive swimming training for all age groups.",
            experience = 8,
            rating = 4.6f,
            reviewCount = 68,
            hourlyRate = 900.0,
            location = "Ahmedabad, GJ",
            specializations = listOf("Freestyle", "Butterfly", "Race Strategy"),
            isAvailable = true
        ),
        Coach(
            uid = "coach5",
            name = "Vikram Singh",
            sport = "Tennis",
            bio = "ITF certified coach. Trained players who compete at state and national levels.",
            experience = 9,
            rating = 4.5f,
            reviewCount = 84,
            hourlyRate = 1200.0,
            location = "Pune, MH",
            specializations = listOf("Serve & Volley", "Baseline Play", "Match Strategy"),
            isAvailable = true
        )
    )

    val sessions = listOf(
        Session(
            id = "sess1",
            coachId = "coach1",
            coachName = "Rajiv Sharma",
            playerId = "player1",
            playerName = "Aman Verma",
            sport = "Cricket",
            date = "2024-09-15",
            timeSlot = "7:00 AM - 8:00 AM",
            status = SessionStatus.CONFIRMED,
            price = 800.0,
            location = "Wankhede Ground, Mumbai"
        ),
        Session(
            id = "sess2",
            coachId = "coach2",
            coachName = "Priya Nair",
            playerId = "player1",
            playerName = "Aman Verma",
            sport = "Badminton",
            date = "2024-09-17",
            timeSlot = "6:00 AM - 7:00 AM",
            status = SessionStatus.PENDING,
            price = 700.0,
            location = "Kanteerava Stadium, Bangalore"
        ),
        Session(
            id = "sess3",
            coachId = "coach3",
            coachName = "Arjun Mehta",
            playerId = "player2",
            playerName = "Rohit Das",
            sport = "Football",
            date = "2024-09-18",
            timeSlot = "5:00 PM - 6:30 PM",
            status = SessionStatus.PENDING,
            price = 1500.0,
            location = "Ambedkar Stadium, Delhi"
        )
    )

    val events = listOf(
        Event(
            id = "evt1",
            organisationId = "org1",
            organisationName = "Mumbai Sports Academy",
            title = "Inter-City Cricket Tournament 2024",
            description = "Annual cricket tournament open to players aged 16-25. Teams of 11.",
            sport = "Cricket",
            date = "2024-10-05",
            location = "Brabourne Stadium, Mumbai",
            fees = 2000.0,
            maxParticipants = 120,
            registeredCount = 87
        ),
        Event(
            id = "evt2",
            organisationId = "org2",
            organisationName = "Delhi Football Club",
            title = "Youth Football League 2024",
            description = "Competitive league for young footballers. Scouts will be present.",
            sport = "Football",
            date = "2024-10-15",
            location = "Jawaharlal Nehru Stadium, Delhi",
            fees = 1500.0,
            maxParticipants = 200,
            registeredCount = 165
        )
    )

    val sports = listOf(
        "Cricket", "Football", "Badminton", "Tennis", "Swimming",
        "Basketball", "Kabaddi", "Volleyball", "Hockey", "Athletics"
    )

    val timeSlots = listOf(
        "5:00 AM - 6:00 AM",
        "6:00 AM - 7:00 AM",
        "7:00 AM - 8:00 AM",
        "8:00 AM - 9:00 AM",
        "4:00 PM - 5:00 PM",
        "5:00 PM - 6:00 PM",
        "6:00 PM - 7:00 PM",
        "7:00 PM - 8:00 PM"
    )
}
