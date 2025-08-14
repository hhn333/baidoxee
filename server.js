const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
const { Server } = require('socket.io');
const http = require('http');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 3000;
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: "*", methods: ["GET", "POST", "PUT", "DELETE"] } });

app.use(cors());
app.use(express.json());

// MongoDB Connection
mongoose.connect('mongodb+srv://chuong:maihuychuong@cluster0.wcohcgr.mongodb.net/parkinglotdb?retryWrites=true&w=majority&appName=Cluster0')
    .then(() => console.log('✅ Kết nối MongoDB thành công'))
    .catch(err => console.error('❌ Lỗi kết nối MongoDB:', err));

// Schemas
const eventSchema = new mongoose.Schema({
    camera_id: String, spot_id: String, spot_name: String,
    event_type: { type: String, enum: ['enter', 'exit'], required: true },
    timestamp: { type: Date, required: true },
    plate_text: { type: String, required: true },
    plate_confidence: { type: Number, default: 0 },
    vehicle_image: String, plate_image: String, location_name: String,
    processed: { type: Boolean, default: false },
    created_at: { type: Date, default: Date.now },
    vehicle_id: String,
    vehicleType: { type: String, enum: ['CAR_UNDER_9', 'CAR_9_TO_16'], default: 'CAR_UNDER_9' }
}, { collection: 'events', timestamps: false, versionKey: false });

const parkingLogSchema = new mongoose.Schema({
    event_enter_id: { type: mongoose.Schema.Types.ObjectId, ref: 'Event', required: true },
    event_exit_id: { type: mongoose.Schema.Types.ObjectId, ref: 'Event', default: null },
    timeIn: { type: Date, required: true },
    timeOut: { type: Date, default: null },
    status: { type: String, enum: ['IN_PROGRESS', 'COMPLETED', 'PAID'], default: 'IN_PROGRESS' },
    fee: { type: Number, default: 0 },
    vehicleType: { type: String, enum: ['CAR_UNDER_9', 'CAR_9_TO_16'], default: 'CAR_UNDER_9' },
    paymentMethod: { type: String, enum: ['CASH', 'BANK_TRANSFER'], default: 'CASH' },
    paymentStatus: { type: String, enum: ['PENDING', 'PAID', 'FAILED'], default: 'PENDING' },
    paymentDate: { type: Date, default: null }
}, { collection: 'parking_logs', timestamps: false, versionKey: false });

const transactionSchema = new mongoose.Schema({
    log: { type: mongoose.Schema.Types.ObjectId, ref: 'ParkingLog', required: true },
    amount: { type: Number, required: true },
    method: { type: String, enum: ['CASH', 'BANK_TRANSFER'], default: 'CASH' },
    status: { type: String, enum: ['PAID', 'PENDING', 'FAILED'], default: 'PENDING' },
    paidAt: { type: Date, default: Date.now }
}, { collection: 'transactions', timestamps: false, versionKey: false });

const Event = mongoose.model('Event', eventSchema);
const ParkingLog = mongoose.model('ParkingLog', parkingLogSchema);
const Transaction = mongoose.model('Transaction', transactionSchema);

// Helper Functions
const safeFormatDate = (dateValue) => {
    if (!dateValue) return null;
    try {
        const date = dateValue instanceof Date ? dateValue : new Date(dateValue);
        return isNaN(date.getTime()) ? null : date.toISOString();
    } catch (error) {
        return null;
    }
};

// Removed fee calculation - only get from database

const mapStatus = {
    'IN_PROGRESS': 'dang_do', 'COMPLETED': 'da_ra', 'PAID': 'da_thanh_toan',
    'CASH': 'tien_mat', 'BANK_TRANSFER': 'chuyen_khoan',
    'PENDING': 'chua_thanh_toan', 'FAILED': 'that_bai'
};

const formatLog = (log) => ({
    _id: log._id,
    bienSoXe: log.event_enter_id?.plate_text || 'N/A',
    thoiGianVao: safeFormatDate(log.timeIn),
    thoiGianRa: safeFormatDate(log.timeOut),
    giaVe: log.fee || 0,
    trangThai: mapStatus[log.status] || 'dang_do',
    trangThaiThanhToan: mapStatus[log.paymentStatus] || 'chua_thanh_toan',
    hinhThucThanhToan: mapStatus[log.paymentMethod] || 'tien_mat',
    thoiGianThanhToan: safeFormatDate(log.paymentDate),
    daThanhToan: log.paymentStatus === 'PAID',
    vehicleType: log.vehicleType || 'CAR_UNDER_9',
    event_enter_id: log.event_enter_id?._id,
    event_exit_id: log.event_exit_id?._id
});

// WebSocket
let connectedClients = new Set();
io.on('connection', (socket) => {
    connectedClients.add(socket.id);

    socket.on('request-payment-list', async () => {
        try {
            const needPaymentLogs = await ParkingLog.find({
                status: 'COMPLETED',
                paymentStatus: { $in: ['PENDING', null] }
            }).populate('event_enter_id').populate('event_exit_id').sort({ timeOut: -1 }).lean();

            const vehicles = needPaymentLogs.map(log => ({
                _id: log._id,
                bienSoXe: log.event_enter_id?.plate_text || 'N/A',
                thoiGianVao: safeFormatDate(log.timeIn),
                thoiGianRa: safeFormatDate(log.timeOut) || safeFormatDate(new Date()),
                giaVe: log.fee || 0,
                trangThaiThanhToan: 'chua_thanh_toan',
                vehicleType: log.vehicleType || 'CAR_UNDER_9'
            }));

            socket.emit('payment-list-updated', { success: true, data: vehicles });
        } catch (error) {
            socket.emit('payment-list-error', { success: false, message: error.message });
        }
    });

    socket.on('disconnect', () => connectedClients.delete(socket.id));
});

// API Routes
app.get('/', (req, res) => res.json({
    message: '🚀 Server Bãi Đỗ Xe Ô Tô',
    version: '4.3.0',
    vehicle_types: ['CAR_UNDER_9 (Xe dưới 9 chỗ)', 'CAR_9_TO_16 (Xe 9-16 chỗ)'],
    pricing: 'Giá vé được lấy từ database, không tự động tính toán'
}));

app.get('/api/test', (req, res) => res.json({
    success: true,
    message: 'Server hoạt động bình thường',
    timestamp: new Date().toISOString(),
    database: mongoose.connection.readyState === 1 ? 'Connected' : 'Disconnected'
}));

// Get all activities
app.get('/api/activities', async (req, res) => {
    try {
        const logs = await ParkingLog.find({}).populate('event_enter_id').populate('event_exit_id').sort({ timeIn: -1 }).lean();
        res.json({ success: true, data: logs.map(formatLog), message: `Tìm thấy ${logs.length} hoạt động` });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// Get vehicles need payment
app.get('/api/vehicles/need-payment', async (req, res) => {
    try {
        const logs = await ParkingLog.find({
            status: 'COMPLETED',
            paymentStatus: { $in: ['PENDING', null] }
        }).populate('event_enter_id').populate('event_exit_id').sort({ timeOut: -1 }).lean();

        const vehicles = logs.map(log => {
            return { ...formatLog(log), giaVe: log.fee || 0, trangThai: 'da_ra', trangThaiThanhToan: 'chua_thanh_toan', daThanhToan: false };
        });

        res.json({ success: true, data: vehicles, message: `${vehicles.length} xe cần thanh toán` });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// Get vehicle by plate number
app.get('/api/activities/license/:plateNumber', async (req, res) => {
    try {
        const { plateNumber } = req.params;
        const enterEvent = await Event.findOne({ plate_text: plateNumber, event_type: 'enter' }).sort({ timestamp: -1 }).lean();

        if (!enterEvent) return res.status(404).json({ success: false, message: 'Không tìm thấy xe' });

        const parkingLog = await ParkingLog.findOne({
            event_enter_id: enterEvent._id,
            $or: [{ paymentStatus: 'PENDING' }, { paymentStatus: { $exists: false } }, { status: { $in: ['IN_PROGRESS', 'COMPLETED'] } }]
        }).sort({ timeIn: -1 }).populate('event_enter_id').populate('event_exit_id').lean();

        if (!parkingLog) return res.status(404).json({ success: false, message: 'Xe đã thanh toán hoặc không có thông tin' });

        let timeOut = parkingLog.timeOut;
        if (!timeOut && parkingLog.status === 'IN_PROGRESS') {
            timeOut = new Date();
            await ParkingLog.findByIdAndUpdate(parkingLog._id, { timeOut, status: 'COMPLETED' });
        }

        res.json({ success: true, data: { ...formatLog({ ...parkingLog, timeOut }) } });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// Update activity
app.put('/api/activities/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const updateData = req.body;
        const mongoUpdate = {};

        if (updateData.thoiGianRa) mongoUpdate.timeOut = new Date(updateData.thoiGianRa);
        if (updateData.giaVe) mongoUpdate.fee = updateData.giaVe;
        if (updateData.trangThai === 'da_ra') mongoUpdate.status = 'COMPLETED';
        if (updateData.trangThai === 'da_thanh_toan') {
            mongoUpdate.status = 'PAID';
            mongoUpdate.paymentStatus = 'PAID';
        }
        if (updateData.hinhThucThanhToan) {
            mongoUpdate.paymentMethod = updateData.hinhThucThanhToan === 'tien_mat' ? 'CASH' : 'BANK_TRANSFER';
        }
        if (updateData.trangThaiThanhToan === 'da_thanh_toan') {
            mongoUpdate.paymentStatus = 'PAID';
            mongoUpdate.paymentDate = new Date();
        }
        if (updateData.vehicleType) mongoUpdate.vehicleType = updateData.vehicleType;

        const updatedLog = await ParkingLog.findByIdAndUpdate(id, mongoUpdate, { new: true })
            .populate('event_enter_id').populate('event_exit_id').lean();

        if (!updatedLog) return res.status(404).json({ success: false, message: 'Không tìm thấy activity' });

        if (mongoUpdate.paymentStatus === 'PAID') {
            await new Transaction({
                log: updatedLog._id,
                amount: updatedLog.fee,
                method: updatedLog.paymentMethod,
                status: 'PAID'
            }).save();
        }

        res.json({ success: true, data: formatLog(updatedLog), message: 'Cập nhật thành công' });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// Get all transactions
app.get('/api/transactions', async (req, res) => {
    try {
        const transactions = await Transaction.find({})
            .populate({ path: 'log', populate: [{ path: 'event_enter_id' }, { path: 'event_exit_id' }] })
            .sort({ paidAt: -1 }).lean();

        const formatted = transactions.map(t => ({
            _id: t._id,
            amount: t.amount,
            method: mapStatus[t.method],
            status: mapStatus[t.status],
            paidAt: safeFormatDate(t.paidAt),
            log: t.log ? {
                _id: t.log._id,
                bienSoXe: t.log.event_enter_id?.plate_text || 'N/A',
                vehicleType: t.log.vehicleType || 'CAR_UNDER_9',
                thoiGianVao: safeFormatDate(t.log.timeIn),
                thoiGianRa: safeFormatDate(t.log.timeOut),
                trangThai: mapStatus[t.log.status],
                fee: t.log.fee
            } : null
        }));

        res.json({ success: true, data: formatted, message: `${formatted.length} giao dịch` });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// Print invoice
app.post('/api/print', (req, res) => {
    res.json({ success: true, message: 'Lệnh in hóa đơn đã được xử lý', data: { printId: Date.now() } });
});

// Get all events
app.get('/api/events', async (req, res) => {
    try {
        const events = await Event.find({}).sort({ timestamp: -1 }).lean();
        res.json({ success: true, data: events, message: `${events.length} events` });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// Get enter plates
app.get('/api/events/enter-plates', async (req, res) => {
    try {
        const events = await Event.find({ event_type: 'enter' })
            .select('plate_text timestamp camera_id spot_name location_name vehicle_id vehicleType processed')
            .sort({ timestamp: -1 }).lean();

        const plates = events.map(e => ({
            _id: e._id,
            bienSoXe: e.plate_text,
            thoiGianVao: safeFormatDate(e.timestamp),
            camera_id: e.camera_id,
            spot_name: e.spot_name,
            location_name: e.location_name,
            vehicle_id: e.vehicle_id,
            vehicleType: e.vehicleType || 'CAR_UNDER_9',
            processed: e.processed || false
        }));

        res.json({ success: true, data: plates, message: `${plates.length} xe vào bãi` });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// Process events
app.post('/api/events/process', async (req, res) => {
    try {
        const unprocessedEvents = await Event.find({ processed: false }).sort({ timestamp: 1 }).lean();
        let processedCount = 0, createdLogs = 0;

        for (const event of unprocessedEvents) {
            try {
                if (event.event_type === 'enter') {
                    await new ParkingLog({
                        event_enter_id: event._id,
                        timeIn: event.timestamp,
                        status: 'IN_PROGRESS',
                        vehicleType: event.vehicleType || 'CAR_UNDER_9'
                    }).save();
                    createdLogs++;
                } else if (event.event_type === 'exit') {
                    const activeLog = await ParkingLog.findOne({ status: 'IN_PROGRESS' })
                        .populate('event_enter_id').sort({ timeIn: -1 });

                    if (activeLog && activeLog.event_enter_id?.plate_text === event.plate_text) {
                        await ParkingLog.findByIdAndUpdate(activeLog._id, {
                            event_exit_id: event._id,
                            timeOut: event.timestamp,
                            status: 'COMPLETED'
                        });
                    }
                }

                await Event.findByIdAndUpdate(event._id, { processed: true });
                processedCount++;
            } catch (eventError) {
                console.error(`Lỗi xử lý event ${event._id}:`, eventError);
            }
        }

        res.json({
            success: true,
            data: { processedEvents: processedCount, createdLogs },
            message: `Xử lý ${processedCount} events, tạo ${createdLogs} parking logs`
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// Recent activities for Android
app.get('/api/recent-activities', async (req, res) => {
    try {
        const limit = parseInt(req.query.limit) || 20;
        const events = await Event.find({}).sort({ timestamp: -1 }).limit(limit * 2).lean();
        const activities = [];

        for (const event of events) {
            let activity = {
                plateNumber: event.plate_text,
                time: new Date(event.timestamp).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', hour12: false }),
                action: event.event_type === 'enter' ? 'IN' : 'OUT',
                fee: 0,
                timestamp: event.timestamp.getTime(),
                _id: event._id.toString()
            };

            if (event.event_type === 'exit') {
                const parkingLog = await ParkingLog.findOne({ event_exit_id: event._id }).populate('event_enter_id').lean();
                if (parkingLog?.event_enter_id) {
                    activity.fee = parkingLog.fee || 0;
                }
            }

            activities.push(activity);
            if (activities.length >= limit) break;
        }

        res.json({ success: true, data: activities, message: `${activities.length} hoạt động gần đây` });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// Dashboard stats
app.get('/api/dashboard-stats', async (req, res) => {
    try {
        const carsParked = await ParkingLog.countDocuments({ status: 'IN_PROGRESS' });

        const today = new Date();
        today.setHours(0, 0, 0, 0);
        const tomorrow = new Date(today);
        tomorrow.setDate(tomorrow.getDate() + 1);

        const todayCars = await ParkingLog.countDocuments({ timeIn: { $gte: today, $lt: tomorrow } });

        const todayRevenue = await Transaction.aggregate([
            { $match: { paidAt: { $gte: today, $lt: tomorrow }, status: 'PAID' } },
            { $group: { _id: null, total: { $sum: '$amount' } } }
        ]);

        const totalSpots = 50;
        const stats = {
            availableSpots: Math.max(0, totalSpots - carsParked),
            carsParked,
            todayCars,
            todayRevenue: todayRevenue.length > 0 ? todayRevenue[0].total : 0,
            totalSpots
        };

        res.json({ success: true, data: stats, message: 'Thống kê thành công' });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// WebSocket status
app.get('/api/realtime-status', (req, res) => {
    res.json({
        success: true,
        websocket_url: `ws://localhost:${PORT}`,
        connected_clients: connectedClients.size,
        message: 'WebSocket server đang hoạt động'
    });
});

// Start server
server.listen(PORT, () => {
    console.log(`🚀 Server Bãi Đỗ Xe Ô Tô chạy tại http://localhost:${PORT}`);
    console.log(`📡 API: http://localhost:${PORT}/api`);
});