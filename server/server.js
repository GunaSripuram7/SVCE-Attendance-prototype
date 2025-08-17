// server/server.js - Socket.IO server for real-time attendance coordination
const express = require('express');
const http = require('http');
const socketIo = require('socket.io');
const cors = require('cors');

const app = express();
const server = http.createServer(app);
const io = socketIo(server, {
  cors: {
    origin: "*",
    methods: ["GET","POST"]
  }
});

app.use(cors());
app.use(express.json());

// In-memory storage
const activeSessions = new Map();           // sessionCode -> session data
const scanningAssignments = new Map();      // rollNumber -> [helperId1, helperId2]
const connectedHelpers = new Map();         // sessionCode -> Set of helper IDs

io.on('connection', (socket) => {
  console.log('New client connected:', socket.id);

  // Teacher creates a new session
  socket.on('createSession', ({ sessionCode, teacherId, maxScanners }) => {
    activeSessions.set(sessionCode, {
      sessionCode,
      teacherId,
      maxScanners,
      status: 'helpers_selection',
      helpers: new Set(),
      createdAt: Date.now()
    });
    connectedHelpers.set(sessionCode, new Set());
    socket.join(`session-${sessionCode}`);
    console.log(`Session created: ${sessionCode} by ${teacherId}`);
  });

  // Teacher assigns a helper
  socket.on('assignHelper', ({ sessionCode, helperRollNumber }) => {
    const session = activeSessions.get(sessionCode);
    if (session) {
      session.helpers.add(helperRollNumber);
      io.to(`session-${sessionCode}`).emit('helperAssigned', {
        helperRollNumber,
        totalHelpers: session.helpers.size
      });
      console.log(`Helper assigned: ${helperRollNumber} to session ${sessionCode}`);
    }
  });

  // Helper joins the scanning session
  socket.on('joinAsHelper', ({ sessionCode, helperRollNumber, deviceId }) => {
    const session = activeSessions.get(sessionCode);
    if (session && session.helpers.has(helperRollNumber)) {
      socket.join(`session-${sessionCode}`);
      connectedHelpers.get(sessionCode).add(helperRollNumber);
      socket.to(`session-${sessionCode}`).emit('helperJoined', {
        helperRollNumber,
        deviceId,
        connectedCount: connectedHelpers.get(sessionCode).size
      });
      console.log(`Helper joined: ${helperRollNumber} in session ${sessionCode}`);
    } else {
      socket.emit('error', { message: 'Not authorized to join this session' });
    }
  });

  // Conflict prevention
  socket.on('requestScanPermission', ({ sessionCode, helperId, detectedRollNumber }) => {
    let assignments = scanningAssignments.get(detectedRollNumber) || [];
    if (assignments.length === 0) {
      assignments.push(helperId);
      scanningAssignments.set(detectedRollNumber, assignments);
      socket.emit('scanPermissionResponse', { rollNumber: detectedRollNumber, granted: true });
    }
    else if (assignments.length === 1 && !assignments.includes(helperId)) {
      assignments.push(helperId);
      scanningAssignments.set(detectedRollNumber, assignments);
      socket.emit('scanPermissionResponse', { rollNumber: detectedRollNumber, granted: true });
    }
    else if (assignments.length >= 2 && !assignments.includes(helperId)) {
      socket.emit('scanPermissionResponse', {
        rollNumber: detectedRollNumber,
        granted: false,
        reason: 'Already assigned to other scanners'
      });
    }
    else {
      socket.emit('scanPermissionResponse', { rollNumber: detectedRollNumber, granted: true });
    }
  });

  // Helper submits scanned student
  socket.on('studentScanned', ({ sessionCode, rollNumber, scannedBy }) => {
    const session = activeSessions.get(sessionCode);
    if (session) {
      session.scannedStudents = session.scannedStudents || new Set();
      if (!session.scannedStudents.has(rollNumber)) {
        session.scannedStudents.add(rollNumber);
        io.to(`session-${sessionCode}`).emit('attendanceUpdate', {
          totalScanned: session.scannedStudents.size,
          latestStudent: rollNumber,
          scannedBy
        });
        console.log(`Student scanned: ${rollNumber} by ${scannedBy} in session ${sessionCode}`);
      }
    }
  });

  // Teacher starts group scanning
  socket.on('startGroupScanning', ({ sessionCode }) => {
    io.to(`session-${sessionCode}`).emit('scanningStarted', {
      sessionCode,
      startTime: Date.now()
    });
    console.log(`Group scanning started for session ${sessionCode}`);
  });

  // Teacher stops scanning
  socket.on('stopScanning', ({ sessionCode, finalCount }) => {
    io.to(`session-${sessionCode}`).emit('scanningEnded', {
      sessionCode,
      finalCount,
      gracePeriodSeconds: 120
    });
    console.log(`Scanning ended for session ${sessionCode} with ${finalCount} students`);
  });

  // Cleanup session data
  socket.on('cleanupSession', ({ sessionCode }) => {
    activeSessions.delete(sessionCode);
    connectedHelpers.delete(sessionCode);
    Array.from(scanningAssignments.keys())
      .forEach(key => scanningAssignments.delete(key));
    console.log(`Session cleaned up: ${sessionCode}`);
  });

  socket.on('disconnect', () => {
    console.log('Client disconnected:', socket.id);
  });
});

// Start server
const PORT = process.env.PORT || 3000;
server.listen(PORT, () => console.log(`Socket.IO server running on port ${PORT}`));

// Health check
app.get('/health', (req, res) =>
  res.json({ status: 'ok', activeSessions: activeSessions.size, timestamp: new Date().toISOString() })
);
