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
    vehicleType: { type: String, enum: ['CAR_UNDER_9', 'CAR_9_TO_16', 'MOTORCYCLE', 'TRUCK', 'BUS'], default: 'CAR_UNDER_9' }
}, { collection: 'events', timestamps: false, versionKey: false });

const parkingLogSchema = new mongoose.Schema({
    id: { type: String, required: true, unique: true },
    vehicle_id: { type: String, required: true },
    slot_id: String, staff_id: String,
    timeIn: { type: Date, required: true },
    timeOut: { type: Date, default: null },
    fee: { type: Number, default: 0 },
    status: { type: String, enum: ['IN_PROGRESS', 'COMPLETED', 'PAID'], default: 'IN_PROGRESS' },
    note: String, entry_event_id: String, exit_event_id: String,
    entry_plate_confidence: { type: Number, default: 0 },
    exit_plate_confidence: { type: Number, default: 0 },
    auto_created: { type: Boolean, default: false },
    parking_duration_minutes: { type: Number, default: 0 },
    blocks_used: { type: Number, default: 0 },
    fee_breakdown: {
        first_block_fee: { type: Number, default: 0 },
        additional_blocks_fee: { type: Number, default: 0 },
        total_fee: { type: Number, default: 0 },
        discount_applied: { type: Number, default: 0 }
    },
    calculated_at: { type: Date, default: null },
    pricing_rule_id: String,
    created_at: { type: Date, default: Date.now },
    paymentMethod: { type: String, enum: ['CASH', 'BANK_TRANSFER'], default: 'CASH' },
    paymentStatus: { type: String, enum: ['PENDING', 'PAID', 'FAILED'], default: 'PENDING' },
    paymentDate: { type: Date, default: null },
    thoiGianChonPhuongThuc: { type: Date, default: null },
    thoiGianThanhToan: { type: Date, default: null }
}, { collection: 'parkinglogs', timestamps: false, versionKey: false });

const vehicleSchema = new mongoose.Schema({
    id: { type: String, required: true, unique: true },
    plateNumber: { type: String, required: true },
    vehicleType: { type: String, enum: ['CAR_UNDER_9', 'CAR_9_TO_16', 'MOTORCYCLE', 'TRUCK', 'BUS'], default: 'CAR_UNDER_9' },
    first_detected_at: { type: Date, default: Date.now },
    last_seen_at: { type: Date, default: Date.now },
    total_detections: { type: Number, default: 0 },
    detection_confidence_avg: { type: Number, default: 0 },
    createdAt: { type: Date, default: Date.now },
    owner: String, phone: String, email: String,
    isActive: { type: Boolean, default: true }
}, { collection: 'vehicles', timestamps: false, versionKey: false });

const transactionSchema = new mongoose.Schema({
    log: { type: String, required: true },
    amount: { type: Number, required: true },
    method: { type: String, enum: ['CASH', 'BANK_TRANSFER'], default: 'CASH' },
    status: { type: String, enum: ['PAID', 'PENDING', 'FAILED'], default: 'PENDING' },
    paidAt: { type: Date, default: Date.now }
}, { collection: 'transactions', timestamps: false, versionKey: false });

const Event = mongoose.model('Event', eventSchema);
const ParkingLog = mongoose.model('ParkingLog', parkingLogSchema);
const Transaction = mongoose.model('Transaction', transactionSchema);
const Vehicle = mongoose.model('Vehicle', vehicleSchema);

// Helper Functions
const safeFormatDate = (dateValue) => {
    if (!dateValue) return null;
    try {
        const date = dateValue instanceof Date ? dateValue : new Date(dateValue);
        return isNaN(date.getTime()) ? null : date.toISOString();
    } catch { return null; }
};

const mapStatus = {
    'IN_PROGRESS': 'dang_do', 'COMPLETED': 'da_ra', 'PAID': 'da_thanh_toan',
    'CASH': 'tien_mat', 'BANK_TRANSFER': 'chuyen_khoan',
    'PENDING': 'chua_thanh_toan', 'FAILED': 'that_bai'
};

const reverseMapStatus = {
    'dang_do': 'IN_PROGRESS', 'da_ra': 'COMPLETED', 'da_thanh_toan': 'PAID',
    'tien_mat': 'CASH', 'chuyen_khoan': 'BANK_TRANSFER',
    'chua_thanh_toan': 'PENDING', 'that_bai': 'FAILED'
};

const getPlateNumber = async (log) => {
    if (log.vehicle_id) {
        try {
            const vehicle = await Vehicle.findOne({ id: log.vehicle_id }).lean();
            if (vehicle?.plateNumber) return vehicle.plateNumber;
        } catch (error) {
            console.error('❌ Error fetching vehicle by id:', error);
        }
    }
    if (log.entry_event_id) {
        try {
            const event = await Event.findOne({ _id: log.entry_event_id }).lean();
            if (event?.plate_text) return event.plate_text;
        } catch (error) {
            console.error('❌ Error fetching event by entry_event_id:', error);
        }
    }
    return 'N/A';
};

const formatLog = async (log) => {
    const plateNumber = await getPlateNumber(log);
    return {
        _id: log._id, id: log.id || log._id,
        bienSoXe: plateNumber, plateNumber: plateNumber,
        thoiGianVao: safeFormatDate(log.timeIn), timeIn: safeFormatDate(log.timeIn),
        thoiGianRa: safeFormatDate(log.timeOut), timeOut: safeFormatDate(log.timeOut),
        giaVe: log.fee || 0, fee: log.fee || 0,
        trangThai: mapStatus[log.status] || 'dang_do', status: log.status,
        trangThaiThanhToan: mapStatus[log.paymentStatus] || 'chua_thanh_toan', paymentStatus: log.paymentStatus,
        hinhThucThanhToan: mapStatus[log.paymentMethod] || 'tien_mat', paymentMethod: log.paymentMethod,
        thoiGianThanhToan: safeFormatDate(log.paymentDate), paymentDate: safeFormatDate(log.paymentDate),
        thoiGianChonPhuongThuc: safeFormatDate(log.thoiGianChonPhuongThuc),
        daThanhToan: log.paymentStatus === 'PAID',
        vehicleType: log.vehicleType || 'CAR_UNDER_9',
        vehicleId: log.vehicle_id, vehicle_id: log.vehicle_id,
        slot_id: log.slot_id, entry_event_id: log.entry_event_id, exit_event_id: log.exit_event_id,
        parking_duration_minutes: log.parking_duration_minutes, blocks_used: log.blocks_used,
        fee_breakdown: log.fee_breakdown
    };
};

// WebSocket
let connectedClients = new Set();
io.on('connection', (socket) => {
    connectedClients.add(socket.id);

    socket.on('request-payment-list', async () => {
        try {
            const needPaymentLogs = await ParkingLog.find({
                status: 'COMPLETED',
                paymentStatus: { $in: ['PENDING', null] }
            }).sort({ timeOut: -1 }).lean();

            const vehicles = [];
            for (const log of needPaymentLogs) {
                const formattedLog = await formatLog(log);
                vehicles.push({ ...formattedLog, giaVe: log.fee || 0, trangThai: 'da_ra', trangThaiThanhToan: 'chua_thanh_toan', daThanhToan: false });
            }
            socket.emit('payment-list-updated', { success: true, data: vehicles });
        } catch (error) {
            socket.emit('payment-list-error', { success: false, message: error.message });
        }
    });

    socket.on('disconnect', () => connectedClients.delete(socket.id));
});

// API Routes
app.get('/', (req, res) => res.json({
    message: '🚀 Server Bãi Đỗ Xe Ô Tô', version: '4.5.0',
    vehicle_types: ['CAR_UNDER_9 (Xe dưới 9 chỗ)', 'CAR_9_TO_16 (Xe 9-16 chỗ)', 'MOTORCYCLE (Xe máy)', 'TRUCK (Xe tải)', 'BUS (Xe buýt)'],
    endpoints: { activities: '/api/activities', parkinglogs: '/api/parkinglogs', events: '/api/events', transactions: '/api/transactions', vehicles: '/api/vehicles', print: '/api/print/invoice' }
}));

app.get('/api/test', (req, res) => res.json({
    success: true, message: 'Server hoạt động bình thường', timestamp: new Date().toISOString(),
    database: mongoose.connection.readyState === 1 ? 'Connected' : 'Disconnected'
}));

// Dashboard endpoints
app.get('/api/dashboard-stats', async (req, res) => {
    try {
        const carsParked = await ParkingLog.countDocuments({ status: 'IN_PROGRESS' });
        const today = new Date(); today.setHours(0, 0, 0, 0);
        const tomorrow = new Date(today); tomorrow.setDate(tomorrow.getDate() + 1);
        const todayCars = await ParkingLog.countDocuments({ timeIn: { $gte: today, $lt: tomorrow } });
        const todayRevenue = await Transaction.aggregate([
            { $match: { paidAt: { $gte: today, $lt: tomorrow }, status: 'PAID' } },
            { $group: { _id: null, total: { $sum: '$amount' } } }
        ]);
        const totalSpots = 50;
        res.json({
            success: true, data: {
                availableSpots: Math.max(0, totalSpots - carsParked),
                carsParked, todayCars, todayRevenue: todayRevenue.length > 0 ? todayRevenue[0].total : 0, totalSpots
            }, message: 'Thống kê thành công'
        });
    } catch (err) { res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message }); }
});

app.get('/api/recent-activities', async (req, res) => {
    try {
        const limit = parseInt(req.query.limit) || 20;
        const logs = await ParkingLog.find({}).sort({ timeIn: -1 }).limit(limit).lean();
        const activities = [];
        for (const log of logs) {
            const plateNumber = await getPlateNumber(log);
            activities.push({
                plateNumber: plateNumber,
                time: new Date(log.timeIn).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', hour12: false }),
                action: log.timeOut ? 'OUT' : 'IN',
                fee: log.fee || 0, timestamp: new Date(log.timeIn).getTime(), _id: log.id || log._id.toString()
            });
        }
        res.json({ success: true, data: activities, message: `${activities.length} hoạt động gần đây` });
    } catch (err) { res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message }); }
});

app.get('/api/realtime-status', (req, res) => {
    res.json({ success: true, websocket_url: `ws://localhost:${PORT}`, connected_clients: connectedClients.size, message: 'WebSocket server đang hoạt động' });
});

// PARKINGLOGS ENDPOINTS
app.get('/api/parkinglogs', async (req, res) => {
    try {
        const logs = await ParkingLog.find({}).sort({ timeIn: -1 }).lean();
        const formattedLogs = [];
        for (const log of logs) formattedLogs.push(await formatLog(log));
        res.json({ success: true, data: formattedLogs, message: `Tìm thấy ${formattedLogs.length} parking logs` });
    } catch (err) { res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message }); }
});

app.get('/api/parkinglogs/need-payment', async (req, res) => {
    try {
        const logs = await ParkingLog.find({
            status: 'COMPLETED', paymentStatus: { $in: ['PENDING', null] }
        }).sort({ timeOut: -1 }).lean();

        const vehicles = [];
        for (const log of logs) {
            const formattedLog = await formatLog(log);
            vehicles.push({ ...formattedLog, giaVe: log.fee || 0, trangThai: 'da_ra', trangThaiThanhToan: 'chua_thanh_toan', daThanhToan: false });
        }
        res.json({ success: true, data: vehicles, message: `${vehicles.length} xe cần thanh toán` });
    } catch (err) { res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message }); }
});

app.get('/api/parkinglogs/license/:plateNumber', async (req, res) => {
    try {
        const { plateNumber } = req.params;
        const vehicle = await Vehicle.findOne({ plateNumber: plateNumber }).lean();
        let parkingLog = null;

        if (vehicle) {
            parkingLog = await ParkingLog.findOne({
                vehicle_id: vehicle.id,
                $or: [{ paymentStatus: 'PENDING' }, { paymentStatus: { $exists: false } }, { status: { $in: ['IN_PROGRESS', 'COMPLETED'] } }]
            }).sort({ timeIn: -1 }).lean();
        }

        if (!parkingLog) {
            const enterEvent = await Event.findOne({ plate_text: plateNumber, event_type: 'enter' }).sort({ timestamp: -1 }).lean();
            if (enterEvent) {
                parkingLog = await ParkingLog.findOne({
                    entry_event_id: enterEvent._id.toString(),
                    $or: [{ paymentStatus: 'PENDING' }, { paymentStatus: { $exists: false } }, { status: { $in: ['IN_PROGRESS', 'COMPLETED'] } }]
                }).sort({ timeIn: -1 }).lean();
            }
        }

        if (!parkingLog) return res.status(404).json({ success: false, message: 'Xe đã thanh toán hoặc không có thông tin' });

        let timeOut = parkingLog.timeOut;
        if (!timeOut && parkingLog.status === 'IN_PROGRESS') {
            timeOut = new Date();
            await ParkingLog.findOneAndUpdate({ id: parkingLog.id }, { timeOut, status: 'COMPLETED' });
        }

        const formattedLog = await formatLog({ ...parkingLog, timeOut });
        res.json({ success: true, data: formattedLog });
    } catch (err) { res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message }); }
});

app.put('/api/parkinglogs/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const updateData = req.body;
        const mongoUpdate = {};

        // Map Vietnamese fields to English
        if (updateData.thoiGianRa) mongoUpdate.timeOut = new Date(updateData.thoiGianRa);
        if (updateData.giaVe !== undefined) mongoUpdate.fee = updateData.giaVe;
        if (updateData.trangThai) mongoUpdate.status = reverseMapStatus[updateData.trangThai] || updateData.trangThai;
        if (updateData.hinhThucThanhToan) {
            mongoUpdate.paymentMethod = updateData.hinhThucThanhToan === 'tien_mat' ? 'CASH' : 'BANK_TRANSFER';
            mongoUpdate.thoiGianChonPhuongThuc = new Date();
        }
        if (updateData.trangThaiThanhToan) {
            mongoUpdate.paymentStatus = reverseMapStatus[updateData.trangThaiThanhToan] || updateData.trangThaiThanhToan;
            if (updateData.trangThaiThanhToan === 'da_thanh_toan') {
                mongoUpdate.paymentDate = new Date();
                mongoUpdate.thoiGianThanhToan = new Date();
            }
        }
        if (updateData.vehicleType) mongoUpdate.vehicleType = updateData.vehicleType;
        if (updateData.vehicle_id) mongoUpdate.vehicle_id = updateData.vehicle_id;

        // Handle direct English fields
        if (updateData.timeOut) mongoUpdate.timeOut = new Date(updateData.timeOut);
        if (updateData.fee !== undefined) mongoUpdate.fee = updateData.fee;
        if (updateData.status) mongoUpdate.status = updateData.status;
        if (updateData.paymentMethod) mongoUpdate.paymentMethod = updateData.paymentMethod;
        if (updateData.paymentStatus) {
            mongoUpdate.paymentStatus = updateData.paymentStatus;
            if (updateData.paymentStatus === 'PAID') {
                mongoUpdate.paymentDate = new Date();
                mongoUpdate.thoiGianThanhToan = new Date();
            }
        }

        const updatedLog = await ParkingLog.findOneAndUpdate({ id: id }, mongoUpdate, { new: true }).lean();
        if (!updatedLog) return res.status(404).json({ success: false, message: 'Không tìm thấy parking log' });

        // Create transaction if payment is completed
        if (mongoUpdate.paymentStatus === 'PAID' && updatedLog.fee > 0) {
            try {
                await new Transaction({ log: updatedLog.id, amount: updatedLog.fee, method: updatedLog.paymentMethod, status: 'PAID' }).save();
            } catch (transError) {
                console.error('❌ Error creating transaction:', transError);
            }
        }

        const formattedLog = await formatLog(updatedLog);
        res.json({ success: true, data: formattedLog, message: 'Cập nhật parking log thành công' });
    } catch (err) { res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message }); }
});

// VEHICLES ENDPOINTS
app.get('/api/vehicles', async (req, res) => {
    try {
        const vehicles = await Vehicle.find({}).sort({ createdAt: -1 }).lean();
        res.json({ success: true, data: vehicles, message: `Tìm thấy ${vehicles.length} vehicles` });
    } catch (err) { res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message }); }
});

app.get('/api/vehicles/:vehicleId', async (req, res) => {
    try {
        const vehicle = await Vehicle.findOne({ id: req.params.vehicleId }).lean();
        if (!vehicle) return res.status(404).json({ success: false, message: 'Không tìm thấy thông tin xe' });
        res.json({ success: true, data: vehicle });
    } catch (error) { res.status(500).json({ success: false, message: 'Lỗi server: ' + error.message }); }
});

app.get('/api/vehicles/by-plate/:plateNumber', async (req, res) => {
    try {
        const vehicle = await Vehicle.findOne({ plateNumber: req.params.plateNumber }).lean();
        if (!vehicle) return res.status(404).json({ success: false, message: 'Không tìm thấy thông tin xe' });
        res.json({ success: true, data: vehicle });
    } catch (error) { res.status(500).json({ success: false, message: 'Lỗi server: ' + error.message }); }
});

// Legacy compatibility - Activities endpoints
app.get('/api/activities', (req, res) => res.redirect('/api/parkinglogs'));
app.get('/api/activities/license/:plateNumber', (req, res) => res.redirect(`/api/parkinglogs/license/${req.params.plateNumber}`));
app.put('/api/activities/:id', (req, res) => res.redirect(307, `/api/parkinglogs/${req.params.id}`));

// Events endpoints
app.get('/api/events', async (req, res) => {
    try {
        const events = await Event.find({}).sort({ timestamp: -1 }).lean();
        res.json({ success: true, data: events, message: `${events.length} events` });
    } catch (err) { res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message }); }
});

// Transactions endpoints
app.get('/api/transactions', async (req, res) => {
    try {
        const transactions = await Transaction.find({}).sort({ paidAt: -1 }).lean();
        const formatted = [];
        for (const t of transactions) {
            const parkingLog = await ParkingLog.findOne({ id: t.log }).lean();
            let plateNumber = 'N/A';
            if (parkingLog) plateNumber = await getPlateNumber(parkingLog);
            formatted.push({
                _id: t._id, amount: t.amount, method: mapStatus[t.method], status: mapStatus[t.status],
                paidAt: safeFormatDate(t.paidAt),
                log: parkingLog ? {
                    _id: parkingLog._id, id: parkingLog.id, bienSoXe: plateNumber, plateNumber: plateNumber,
                    vehicleType: parkingLog.vehicleType || 'CAR_UNDER_9',
                    thoiGianVao: safeFormatDate(parkingLog.timeIn), thoiGianRa: safeFormatDate(parkingLog.timeOut),
                    trangThai: mapStatus[parkingLog.status], fee: parkingLog.fee
                } : null
            });
        }
        res.json({ success: true, data: formatted, message: `${formatted.length} giao dịch` });
    } catch (err) { res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message }); }
});

// Print endpoints
app.post('/api/print/invoice', (req, res) => {
    try {
        const { bienSoXe, activityId, timestamp } = req.body;
        if (!bienSoXe) return res.status(400).json({ success: false, message: 'Thiếu thông tin biển số xe' });
        const printId = timestamp || Date.now();
        res.json({ success: true, message: 'Lệnh in hóa đơn đã được xử lý', data: { printId, bienSoXe, activityId, status: 'queued' } });
    } catch (error) { res.status(500).json({ success: false, message: 'Lỗi xử lý lệnh in: ' + error.message }); }
});

app.post('/api/print', (req, res) => res.redirect(307, '/api/print/invoice'));

// Additional parking logs endpoints (shortened similar pattern)
['paid', 'unpaid'].forEach(type => {
    app.get(`/api/parkinglogs/${type}`, async (req, res) => {
        try {
            const query = type === 'paid' ? { paymentStatus: 'PAID' } : { paymentStatus: { $in: ['PENDING', null] }, status: 'COMPLETED' };
            const logs = await ParkingLog.find(query).sort(type === 'paid' ? { paymentDate: -1 } : { timeOut: -1 }).lean();
            const formattedLogs = [];
            for (const log of logs) formattedLogs.push(await formatLog(log));
            res.json({ success: true, data: formattedLogs, message: `Tìm thấy ${formattedLogs.length} parking logs ${type === 'paid' ? 'đã thanh toán' : 'chưa thanh toán'}` });
        } catch (err) { res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message }); }
    });
});

app.get('/api/parkinglogs/by-date/:date', async (req, res) => {
    try {
        const { date } = req.params;
        const startDate = new Date(date); startDate.setHours(0, 0, 0, 0);
        const endDate = new Date(date); endDate.setHours(23, 59, 59, 999);
        const logs = await ParkingLog.find({ timeIn: { $gte: startDate, $lte: endDate } }).sort({ timeIn: -1 }).lean();
        const formattedLogs = [];
        for (const log of logs) formattedLogs.push(await formatLog(log));
        res.json({ success: true, data: formattedLogs, message: `Tìm thấy ${formattedLogs.length} parking logs ngày ${date}` });
    } catch (err) { res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message }); }
});

app.get('/api/parkinglogs/by-vehicle/:vehicleId', async (req, res) => {
    try {
        const logs = await ParkingLog.find({ vehicle_id: req.params.vehicleId }).sort({ timeIn: -1 }).lean();
        if (!logs.length) return res.json({ success: false, message: 'Không tìm thấy parking logs cho vehicle ID này' });
        const formattedLogs = [];
        for (const log of logs) formattedLogs.push(await formatLog(log));
        res.json({ success: true, data: formattedLogs, message: `Tìm thấy ${formattedLogs.length} parking logs` });
    } catch (error) { res.status(500).json({ success: false, message: 'Lỗi server: ' + error.message }); }
});

app.get('/api/parkinglogs/stats', async (req, res) => {
    try {
        const [totalLogs, inProgress, completed, paid] = await Promise.all([
            ParkingLog.countDocuments(),
            ParkingLog.countDocuments({ status: 'IN_PROGRESS' }),
            ParkingLog.countDocuments({ status: 'COMPLETED' }),
            ParkingLog.countDocuments({ status: 'PAID' })
        ]);

        const [totalRevenue, todayRevenue] = await Promise.all([
            ParkingLog.aggregate([{ $match: { paymentStatus: 'PAID' } }, { $group: { _id: null, total: { $sum: '$fee' } } }]),
            ParkingLog.aggregate([{
                $match: {
                    paymentStatus: 'PAID',
                    paymentDate: { $gte: new Date(new Date().setHours(0, 0, 0, 0)), $lt: new Date(new Date().setHours(23, 59, 59, 999)) }
                }
            }, { $group: { _id: null, total: { $sum: '$fee' } } }])
        ]);

        res.json({
            success: true,
            data: {
                totalLogs, inProgress, completed, paid,
                totalRevenue: totalRevenue.length > 0 ? totalRevenue[0].total : 0,
                todayRevenue: todayRevenue.length > 0 ? todayRevenue[0].total : 0
            },
            message: 'Thống kê parking logs thành công'
        });
    } catch (err) { res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message }); }
});

// Debug endpoints
app.get('/api/debug/collections', async (req, res) => {
    try {
        const collections = await mongoose.connection.db.listCollections().toArray();
        const collectionNames = collections.map(c => c.name);
        const stats = {};
        for (const name of collectionNames) {
            if (['vehicles', 'parkinglogs', 'events', 'transactions'].includes(name)) {
                stats[name] = await mongoose.connection.db.collection(name).countDocuments();
            }
        }
        res.json({ success: true, collections: collectionNames, stats, message: 'Database connection OK' });
    } catch (error) { res.status(500).json({ success: false, message: 'Database error: ' + error.message }); }
});

app.get('/api/debug/vehicle-parking/:plateNumber', async (req, res) => {
    try {
        const { plateNumber } = req.params;
        const vehicle = await Vehicle.findOne({ plateNumber }).lean();
        let parkingLogs = [];
        if (vehicle) parkingLogs = await ParkingLog.find({ vehicle_id: vehicle.id }).lean();
        res.json({
            success: true,
            data: { plateNumber, vehicle, parkingLogs, relationship: vehicle ? `vehicle.id (${vehicle.id}) -> parkinglogs.vehicle_id` : 'No vehicle found' }
        });
    } catch (error) { res.status(500).json({ success: false, message: 'Debug error: ' + error.message }); }
});

// Error handling
app.use((err, req, res, next) => {
    console.error('❌ Unhandled error:', err);
    res.status(500).json({ success: false, message: 'Lỗi server không xác định' });
});

app.use((req, res) => {
    res.status(404).json({
        success: false,
        message: `Route không tồn tại: ${req.method} ${req.originalUrl}`,
        availableRoutes: {
            parkinglogs: '/api/parkinglogs',
            parkinglogs_need_payment: '/api/parkinglogs/need-payment',
            parkinglogs_by_license: '/api/parkinglogs/license/:plateNumber',
            vehicles: '/api/vehicles',
            vehicles_by_plate: '/api/vehicles/by-plate/:plateNumber',
            vehicles_by_id: '/api/vehicles/:vehicleId',
            transactions: '/api/transactions',
            events: '/api/events',
            print: '/api/print/invoice',
            dashboard: '/api/dashboard-stats',
            debug: '/api/debug/collections'
        }
    });
});

// Start server
server.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 Server Bãi Đỗ Xe Ô Tô chạy tại http://localhost:${PORT}`);
    console.log(`📡 API: http://localhost:${PORT}/api`);
});