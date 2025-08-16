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
    camera_id: String,
    spot_id: String,
    spot_name: String,
    event_type: { type: String, enum: ['enter', 'exit'], required: true },
    timestamp: { type: Date, required: true },
    plate_text: { type: String, required: true },
    plate_confidence: { type: Number, default: 0 },
    vehicle_image: String,
    plate_image: String,
    location_name: String,
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
    paymentDate: { type: Date, default: null },
    created_at: { type: Date, default: Date.now },
    updated_at: { type: Date, default: Date.now }
}, { collection: 'parkinglogs', timestamps: false, versionKey: false });

const transactionSchema = new mongoose.Schema({
    parkinglog: { type: mongoose.Schema.Types.ObjectId, ref: 'ParkingLog', required: true },
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

const mapStatus = {
    'IN_PROGRESS': 'dang_do',
    'COMPLETED': 'da_ra',
    'PAID': 'da_thanh_toan',
    'CASH': 'tien_mat',
    'BANK_TRANSFER': 'chuyen_khoan',
    'PENDING': 'chua_thanh_toan',
    'PAID': 'da_thanh_toan',
    'FAILED': 'that_bai'
};

// FIX 1: Cải thiện formatLog function để handle null values
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
    event_exit_id: log.event_exit_id?._id,
    status: log.status,
    paymentStatus: log.paymentStatus, // Add original status
    needsPaymentDisplay: log.status === 'COMPLETED' && log.paymentStatus !== 'PAID',
    // FIX: Add vehicle_id for Android tracking
    vehicle_id: log.event_enter_id?.vehicle_id || null
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
                vehicleType: log.vehicleType || 'CAR_UNDER_9',
                status: 'COMPLETED',
                needsPaymentDisplay: true,
                vehicle_id: log.event_enter_id?.vehicle_id || null
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
    message: '🚀 Server Bãi Đỗ Xe Ô Tô - Updated with parkinglogs',
    version: '4.5.0',
    collections: ['events', 'parkinglogs', 'transactions'],
    vehicle_types: ['CAR_UNDER_9 (Xe dưới 9 chỗ)', 'CAR_9_TO_16 (Xe 9-16 chỗ)'],
    note: 'Fixed for Android compatibility'
}));

app.get('/api/test', (req, res) => res.json({
    success: true,
    message: 'Server hoạt động bình thường',
    timestamp: new Date().toISOString(),
    database: mongoose.connection.readyState === 1 ? 'Connected' : 'Disconnected',
    collections: 'events, parkinglogs, transactions'
}));

// Get all activities từ parkinglogs
app.get('/api/activities', async (req, res) => {
    try {
        const logs = await ParkingLog.find({})
            .populate('event_enter_id')
            .populate('event_exit_id')
            .sort({ timeIn: -1 })
            .lean();

        res.json({
            success: true,
            data: logs.map(formatLog),
            message: `Tìm thấy ${logs.length} hoạt động từ parkinglogs`
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// Get vehicles need payment từ parkinglogs với status = COMPLETED
app.get('/api/vehicles/need-payment', async (req, res) => {
    try {
        const logs = await ParkingLog.find({
            status: 'COMPLETED',
            paymentStatus: { $in: ['PENDING', null] }
        }).populate('event_enter_id').populate('event_exit_id').sort({ timeOut: -1 }).lean();

        const vehicles = logs.map(log => {
            const formattedLog = formatLog(log);
            return {
                ...formattedLog,
                giaVe: log.fee || 0,
                trangThai: 'da_ra',
                trangThaiThanhToan: 'chua_thanh_toan',
                daThanhToan: false,
                needsPaymentDisplay: true
            };
        });

        res.json({
            success: true,
            data: vehicles,
            message: `${vehicles.length} xe cần thanh toán (status=COMPLETED từ parkinglogs)`
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// FIX 2: Cải thiện API get vehicle by plate number
app.get('/api/activities/license/:plateNumber', async (req, res) => {
    try {
        const { plateNumber } = req.params;

        // Tìm enter event từ events collection
        const enterEvent = await Event.findOne({
            plate_text: plateNumber,
            event_type: 'enter'
        }).sort({ timestamp: -1 }).lean();

        if (!enterEvent) {
            return res.status(404).json({
                success: false,
                message: 'Không tìm thấy xe trong events collection',
                data: null
            });
        }

        // Tìm parking log từ parkinglogs collection
        const parkingLog = await ParkingLog.findOne({
            event_enter_id: enterEvent._id,
            $or: [
                { paymentStatus: 'PENDING' },
                { paymentStatus: { $exists: false } },
                { paymentStatus: null },
                { status: { $in: ['IN_PROGRESS', 'COMPLETED'] } }
            ]
        }).sort({ timeIn: -1 })
            .populate('event_enter_id')
            .populate('event_exit_id')
            .lean();

        if (!parkingLog) {
            return res.status(404).json({
                success: false,
                message: 'Xe đã thanh toán hoặc không có thông tin trong parkinglogs',
                data: null
            });
        }

        // Tự động cập nhật timeOut và status nếu cần
        let timeOut = parkingLog.timeOut;
        let updatedStatus = parkingLog.status;
        let fee = parkingLog.fee;

        if (!timeOut && parkingLog.status === 'IN_PROGRESS') {
            timeOut = new Date();
            updatedStatus = 'COMPLETED';

            // FIX 3: Tính phí tự động nếu chưa có
            if (!fee || fee === 0) {
                fee = calculateParkingFee(parkingLog.timeIn, timeOut, parkingLog.vehicleType);
            }

            await ParkingLog.findByIdAndUpdate(parkingLog._id, {
                timeOut,
                status: 'COMPLETED',
                fee,
                updated_at: new Date()
            });
        }

        const responseData = formatLog({
            ...parkingLog,
            timeOut,
            status: updatedStatus,
            fee
        });

        res.json({
            success: true,
            data: responseData,
            message: 'Thông tin xe từ parkinglogs'
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// FIX 4: Cải thiện API update activity
app.put('/api/activities/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const updateData = req.body;
        const mongoUpdate = { updated_at: new Date() };

        // Map update fields
        if (updateData.thoiGianRa) {
            mongoUpdate.timeOut = new Date(updateData.thoiGianRa);
        }
        if (updateData.giaVe !== undefined) {
            mongoUpdate.fee = parseInt(updateData.giaVe) || 0;
        }
        if (updateData.trangThai === 'da_ra') {
            mongoUpdate.status = 'COMPLETED';
        }
        if (updateData.trangThai === 'da_thanh_toan') {
            mongoUpdate.status = 'PAID';
            mongoUpdate.paymentStatus = 'PAID';
            mongoUpdate.paymentDate = new Date();
        }
        if (updateData.hinhThucThanhToan) {
            mongoUpdate.paymentMethod = updateData.hinhThucThanhToan === 'tien_mat' ? 'CASH' : 'BANK_TRANSFER';
        }
        if (updateData.trangThaiThanhToan === 'da_thanh_toan') {
            mongoUpdate.paymentStatus = 'PAID';
            mongoUpdate.paymentDate = new Date();
        }
        if (updateData.vehicleType) {
            mongoUpdate.vehicleType = updateData.vehicleType;
        }

        // FIX: Handle payment processing
        if (updateData.paymentStatus === 'PAID') {
            mongoUpdate.paymentStatus = 'PAID';
            mongoUpdate.paymentDate = new Date();
        }
        if (updateData.paymentMethod) {
            mongoUpdate.paymentMethod = updateData.paymentMethod;
        }
        if (updateData.fee !== undefined) {
            mongoUpdate.fee = parseInt(updateData.fee) || 0;
        }

        const updatedLog = await ParkingLog.findByIdAndUpdate(id, mongoUpdate, { new: true })
            .populate('event_enter_id')
            .populate('event_exit_id')
            .lean();

        if (!updatedLog) {
            return res.status(404).json({
                success: false,
                message: 'Không tìm thấy activity trong parkinglogs'
            });
        }

        // Tạo transaction nếu thanh toán thành công
        if (mongoUpdate.paymentStatus === 'PAID' && updatedLog.fee > 0) {
            await new Transaction({
                parkinglog: updatedLog._id,
                amount: updatedLog.fee,
                method: updatedLog.paymentMethod,
                status: 'PAID'
            }).save();
        }

        res.json({
            success: true,
            data: formatLog(updatedLog),
            message: 'Cập nhật parkinglogs thành công'
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// FIX 5: Thêm API riêng cho payment processing
app.put('/api/parkinglogs/:id/payment', async (req, res) => {
    try {
        const { id } = req.params;
        const paymentData = req.body;

        const updateData = {
            paymentStatus: paymentData.paymentStatus || 'PAID',
            paymentMethod: paymentData.paymentMethod || 'CASH',
            paymentDate: new Date(),
            fee: parseInt(paymentData.fee) || 0,
            updated_at: new Date()
        };

        if (paymentData.status) {
            updateData.status = paymentData.status;
        }

        const updatedLog = await ParkingLog.findByIdAndUpdate(id, updateData, { new: true })
            .populate('event_enter_id')
            .populate('event_exit_id')
            .lean();

        if (!updatedLog) {
            return res.status(404).json({
                success: false,
                message: 'Không tìm thấy parking log'
            });
        }

        // Tạo transaction
        if (updatedLog.paymentStatus === 'PAID' && updatedLog.fee > 0) {
            await new Transaction({
                parkinglog: updatedLog._id,
                amount: updatedLog.fee,
                method: updatedLog.paymentMethod,
                status: 'PAID'
            }).save();
        }

        res.json({
            success: true,
            data: formatLog(updatedLog),
            message: 'Xử lý thanh toán thành công'
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi xử lý thanh toán: ' + err.message });
    }
});

// FIX 6: Thêm API để lấy combined payment info
app.get('/api/payment-info/:plateNumber', async (req, res) => {
    try {
        const { plateNumber } = req.params;

        // Get events
        const events = await Event.find({
            plate_text: plateNumber
        }).sort({ timestamp: -1 }).lean();

        if (events.length === 0) {
            return res.status(404).json({
                success: false,
                message: 'Không tìm thấy events cho xe này'
            });
        }

        // Get parking logs
        const enterEventIds = events
            .filter(e => e.event_type === 'enter')
            .map(e => e._id);

        const parkingLogs = await ParkingLog.find({
            event_enter_id: { $in: enterEventIds }
        }).populate('event_enter_id')
            .populate('event_exit_id')
            .sort({ timeIn: -1 })
            .lean();

        // Find latest events
        const latestEnter = events.find(e => e.event_type === 'enter');
        const latestExit = events.find(e => e.event_type === 'exit');

        res.json({
            success: true,
            data: {
                plate_text: plateNumber,
                vehicle_id: latestEnter?.vehicle_id || '',
                latest_enter: latestEnter || null,
                latest_exit: latestExit || null,
                events: {
                    success: true,
                    data: events
                },
                parking_logs: {
                    success: true,
                    data: parkingLogs.map(formatLog)
                }
            },
            message: 'Thông tin thanh toán đầy đủ'
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// Get all transactions từ parkinglogs
app.get('/api/transactions', async (req, res) => {
    try {
        const transactions = await Transaction.find({})
            .populate({
                path: 'parkinglog',
                populate: [
                    { path: 'event_enter_id' },
                    { path: 'event_exit_id' }
                ]
            })
            .sort({ paidAt: -1 })
            .lean();

        const formatted = transactions.map(t => ({
            _id: t._id,
            amount: t.amount,
            method: mapStatus[t.method],
            status: mapStatus[t.status],
            paidAt: safeFormatDate(t.paidAt),
            parkinglog: t.parkinglog ? {
                _id: t.parkinglog._id,
                bienSoXe: t.parkinglog.event_enter_id?.plate_text || 'N/A',
                vehicleType: t.parkinglog.vehicleType || 'CAR_UNDER_9',
                thoiGianVao: safeFormatDate(t.parkinglog.timeIn),
                thoiGianRa: safeFormatDate(t.parkinglog.timeOut),
                trangThai: mapStatus[t.parkinglog.status],
                fee: t.parkinglog.fee
            } : null
        }));

        res.json({
            success: true,
            data: formatted,
            message: `${formatted.length} giao dịch từ parkinglogs`
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// Print invoice - FIX 7: Cải thiện response
app.post('/api/print', (req, res) => {
    const printData = req.body;

    // Log print request
    console.log('🖨️ Print request received:', printData);

    res.json({
        success: true,
        message: 'Lệnh in hóa đơn đã được xử lý từ parkinglogs',
        data: {
            printId: Date.now(),
            plateNumber: printData.bienSoXe || printData.plateNumber,
            fee: printData.giaVe || printData.fee,
            printTime: new Date().toISOString()
        }
    });
});

// Get all events
app.get('/api/events', async (req, res) => {
    try {
        const events = await Event.find({}).sort({ timestamp: -1 }).lean();
        res.json({
            success: true,
            data: events,
            message: `${events.length} events (biển số xe từ plate_text)`
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// Get enter plates từ events
app.get('/api/events/enter-plates', async (req, res) => {
    try {
        const events = await Event.find({ event_type: 'enter' })
            .select('plate_text timestamp camera_id spot_name location_name vehicle_id vehicleType processed')
            .sort({ timestamp: -1 })
            .lean();

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

        res.json({
            success: true,
            data: plates,
            message: `${plates.length} xe vào bãi (từ events.plate_text)`
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// Process events và tạo parkinglogs
app.post('/api/events/process', async (req, res) => {
    try {
        const unprocessedEvents = await Event.find({ processed: false })
            .sort({ timestamp: 1 })
            .lean();

        let processedCount = 0, createdLogs = 0;

        for (const event of unprocessedEvents) {
            try {
                if (event.event_type === 'enter') {
                    // Tạo parking log mới trong parkinglogs
                    await new ParkingLog({
                        event_enter_id: event._id,
                        timeIn: event.timestamp,
                        status: 'IN_PROGRESS',
                        vehicleType: event.vehicleType || 'CAR_UNDER_9',
                        created_at: new Date(),
                        updated_at: new Date()
                    }).save();
                    createdLogs++;
                } else if (event.event_type === 'exit') {
                    // Tìm active log trong parkinglogs
                    const activeLog = await ParkingLog.findOne({ status: 'IN_PROGRESS' })
                        .populate('event_enter_id')
                        .sort({ timeIn: -1 });

                    if (activeLog && activeLog.event_enter_id?.plate_text === event.plate_text) {
                        // Tính phí tự động
                        const fee = calculateParkingFee(activeLog.timeIn, event.timestamp, activeLog.vehicleType);

                        // Cập nhật status thành COMPLETED khi xe ra
                        await ParkingLog.findByIdAndUpdate(activeLog._id, {
                            event_exit_id: event._id,
                            timeOut: event.timestamp,
                            status: 'COMPLETED',
                            fee: fee,
                            updated_at: new Date()
                        });

                        // Emit WebSocket notification khi status = COMPLETED
                        io.emit('vehicle-needs-payment', {
                            success: true,
                            data: {
                                _id: activeLog._id,
                                plate_text: event.plate_text,
                                status: 'COMPLETED',
                                timeOut: event.timestamp,
                                fee: fee,
                                needsPaymentDisplay: true
                            },
                            message: `Xe ${event.plate_text} cần thanh toán`
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
            message: `Xử lý ${processedCount} events, tạo ${createdLogs} parkinglogs`
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// Recent activities từ events và parkinglogs
app.get('/api/recent-activities', async (req, res) => {
    try {
        const limit = parseInt(req.query.limit) || 20;
        const events = await Event.find({}).sort({ timestamp: -1 }).limit(limit * 2).lean();
        const activities = [];

        for (const event of events) {
            let activity = {
                plateNumber: event.plate_text,
                time: new Date(event.timestamp).toLocaleTimeString('vi-VN', {
                    hour: '2-digit',
                    minute: '2-digit',
                    hour12: false
                }),
                action: event.event_type === 'enter' ? 'IN' : 'OUT',
                fee: 0,
                timestamp: event.timestamp.getTime(),
                _id: event._id.toString()
            };

            // Nếu là exit event, lấy fee từ parkinglogs
            if (event.event_type === 'exit') {
                const parkingLog = await ParkingLog.findOne({ event_exit_id: event._id })
                    .populate('event_enter_id')
                    .lean();
                if (parkingLog?.event_enter_id) {
                    activity.fee = parkingLog.fee || 0;
                }
            }

            activities.push(activity);
            if (activities.length >= limit) break;
        }

        res.json({
            success: true,
            data: activities,
            message: `${activities.length} hoạt động gần đây (biển số từ events, phí từ parkinglogs)`
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// Dashboard stats từ parkinglogs
app.get('/api/dashboard-stats', async (req, res) => {
    try {
        const carsParked = await ParkingLog.countDocuments({ status: 'IN_PROGRESS' });

        const today = new Date();
        today.setHours(0, 0, 0, 0);
        const tomorrow = new Date(today);
        tomorrow.setDate(tomorrow.getDate() + 1);

        const todayCars = await ParkingLog.countDocuments({
            timeIn: { $gte: today, $lt: tomorrow }
        });

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

        res.json({
            success: true,
            data: stats,
            message: 'Thống kê từ parkinglogs thành công'
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// API riêng cho parkinglogs
app.get('/api/parkinglogs', async (req, res) => {
    try {
        const logs = await ParkingLog.find({})
            .populate('event_enter_id')
            .populate('event_exit_id')
            .sort({ created_at: -1 })
            .lean();

        const formattedLogs = logs.map(log => ({
            _id: log._id,
            bienSoXe: log.event_enter_id?.plate_text || 'N/A',
            event_enter_id: log.event_enter_id?._id,
            event_exit_id: log.event_exit_id?._id,
            timeIn: safeFormatDate(log.timeIn),
            timeOut: safeFormatDate(log.timeOut),
            status: log.status,
            fee: log.fee || 0,
            vehicleType: log.vehicleType || 'CAR_UNDER_9',
            paymentMethod: log.paymentMethod,
            paymentStatus: log.paymentStatus,
            paymentDate: safeFormatDate(log.paymentDate),
            needsPaymentDisplay: log.status === 'COMPLETED' && log.paymentStatus !== 'PAID',
            vehicle_id: log.event_enter_id?.vehicle_id || null,
            created_at: safeFormatDate(log.created_at),
            updated_at: safeFormatDate(log.updated_at)
        }));

        res.json({
            success: true,
            data: formattedLogs,
            message: `${formattedLogs.length} records từ parkinglogs collection`
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// API để lấy parkinglogs theo biển số xe
app.get('/api/parkinglogs/by-plate/:plateNumber', async (req, res) => {
    try {
        const { plateNumber } = req.params;

        // Tìm tất cả enter events của biển số này
        const enterEvents = await Event.find({
            plate_text: plateNumber,
            event_type: 'enter'
        }).sort({ timestamp: -1 });

        if (enterEvents.length === 0) {
            return res.status(404).json({
                success: false,
                message: 'Không tìm thấy xe trong events collection'
            });
        }

        // Lấy parkinglogs tương ứng
        const enterEventIds = enterEvents.map(e => e._id);
        const parkingLogs = await ParkingLog.find({
            event_enter_id: { $in: enterEventIds }
        }).populate('event_enter_id')
            .populate('event_exit_id')
            .sort({ timeIn: -1 })
            .lean();

        const formattedLogs = parkingLogs.map(log => ({
            _id: log._id,
            bienSoXe: log.event_enter_id?.plate_text || plateNumber,
            event_enter_id: log.event_enter_id?._id,
            event_exit_id: log.event_exit_id?._id,
            timeIn: safeFormatDate(log.timeIn),
            timeOut: safeFormatDate(log.timeOut),
            status: log.status,
            fee: log.fee || 0,
            vehicleType: log.vehicleType || 'CAR_UNDER_9',
            paymentMethod: log.paymentMethod,
            paymentStatus: log.paymentStatus,
            paymentDate: safeFormatDate(log.paymentDate),
            needsPaymentDisplay: log.status === 'COMPLETED' && log.paymentStatus !== 'PAID',
            vehicle_id: log.event_enter_id?.vehicle_id || null
        }));

        res.json({
            success: true,
            data: formattedLogs,
            message: `${formattedLogs.length} parkinglogs cho xe ${plateNumber}`
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi server: ' + err.message });
    }
});

// FIX 8: Helper function để tính phí đỗ xe
function calculateParkingFee(timeIn, timeOut, vehicleType) {
    try {
        const startTime = new Date(timeIn);
        const endTime = new Date(timeOut);

        if (isNaN(startTime.getTime()) || isNaN(endTime.getTime())) {
            console.error('Invalid date format for fee calculation');
            return 0;
        }

        const diffInMillis = endTime.getTime() - startTime.getTime();
        let diffInHours = Math.ceil(diffInMillis / (1000 * 60 * 60)); // Làm tròn lên giờ

        // Minimum 1 hour
        if (diffInHours < 1) {
            diffInHours = 1;
        }

        // Phí theo loại xe
        let hourlyRate;
        switch (vehicleType) {
            case 'CAR_UNDER_9':
                hourlyRate = 10000; // 10k/hour
                break;
            case 'CAR_9_TO_16':
                hourlyRate = 15000; // 15k/hour
                break;
            case 'MOTORBIKE':
                hourlyRate = 5000; // 5k/hour
                break;
            case 'TRUCK':
                hourlyRate = 20000; // 20k/hour
                break;
            default:
                hourlyRate = 10000; // Default rate
        }

        const totalFee = diffInHours * hourlyRate;

        console.log(`Fee calculation: ${diffInHours} hours × ${hourlyRate} = ${totalFee} VND`);

        return totalFee;
    } catch (error) {
        console.error('Error calculating parking fee:', error);
        return 0;
    }
}

// FIX 9: Thêm API để tạo parking log mới
app.post('/api/parkinglogs', async (req, res) => {
    try {
        const parkingData = req.body;

        const newParkingLog = new ParkingLog({
            event_enter_id: parkingData.event_enter_id,
            event_exit_id: parkingData.event_exit_id || null,
            timeIn: parkingData.timeIn || new Date(),
            timeOut: parkingData.timeOut || null,
            status: parkingData.status || 'IN_PROGRESS',
            fee: parseInt(parkingData.fee) || 0,
            vehicleType: parkingData.vehicleType || 'CAR_UNDER_9',
            paymentMethod: parkingData.paymentMethod || 'CASH',
            paymentStatus: parkingData.paymentStatus || 'PENDING',
            created_at: new Date(),
            updated_at: new Date()
        });

        const savedLog = await newParkingLog.save();

        // Populate references
        await savedLog.populate('event_enter_id');
        await savedLog.populate('event_exit_id');

        res.json({
            success: true,
            data: formatLog(savedLog.toObject()),
            message: 'Tạo parking log thành công'
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi tạo parking log: ' + err.message });
    }
});

// FIX 10: Thêm API để check và tự động tạo parking log từ events
app.post('/api/sync-events-to-parkinglogs', async (req, res) => {
    try {
        let createdLogs = 0;
        let updatedLogs = 0;

        // Lấy tất cả enter events chưa có parking log
        const enterEvents = await Event.find({
            event_type: 'enter',
            processed: true
        }).sort({ timestamp: 1 });

        for (const enterEvent of enterEvents) {
            // Check xem đã có parking log chưa
            const existingLog = await ParkingLog.findOne({
                event_enter_id: enterEvent._id
            });

            if (!existingLog) {
                // Tìm exit event tương ứng
                const exitEvent = await Event.findOne({
                    event_type: 'exit',
                    plate_text: enterEvent.plate_text,
                    timestamp: { $gt: enterEvent.timestamp },
                    processed: true
                }).sort({ timestamp: 1 });

                // Tạo parking log mới
                const parkingLogData = {
                    event_enter_id: enterEvent._id,
                    timeIn: enterEvent.timestamp,
                    vehicleType: enterEvent.vehicleType || 'CAR_UNDER_9',
                    status: 'IN_PROGRESS'
                };

                if (exitEvent) {
                    parkingLogData.event_exit_id = exitEvent._id;
                    parkingLogData.timeOut = exitEvent.timestamp;
                    parkingLogData.status = 'COMPLETED';
                    parkingLogData.fee = calculateParkingFee(
                        enterEvent.timestamp,
                        exitEvent.timestamp,
                        enterEvent.vehicleType || 'CAR_UNDER_9'
                    );
                }

                await new ParkingLog(parkingLogData).save();
                createdLogs++;
            }
        }

        res.json({
            success: true,
            data: { createdLogs, updatedLogs },
            message: `Đồng bộ thành công: tạo ${createdLogs} parking logs mới`
        });
    } catch (err) {
        res.status(500).json({ success: false, message: 'Lỗi đồng bộ: ' + err.message });
    }
});

// WebSocket status
app.get('/api/realtime-status', (req, res) => {
    res.json({
        success: true,
        websocket_url: `ws://localhost:${PORT}`,
        connected_clients: connectedClients.size,
        message: 'WebSocket server đang hoạt động cho parkinglogs'
    });
});

// FIX 11: Thêm middleware để log requests cho debugging
app.use((req, res, next) => {
    if (req.method !== 'GET' || req.url.includes('api')) {
        console.log(`${new Date().toISOString()} - ${req.method} ${req.url}`);
        if (req.body && Object.keys(req.body).length > 0) {
            console.log('Request body:', JSON.stringify(req.body, null, 2));
        }
    }
    next();
});

// Start server
server.listen(PORT, () => {
    console.log(`🚀 Server Bãi Đỗ Xe Ô Tô chạy tại http://localhost:${PORT}`);
    console.log(`📡 API: http://localhost:${PORT}/api`);
    console.log(`📊 Collections: events, parkinglogs, transactions`);
    console.log(`🔔 WebSocket: Real-time notifications khi status = COMPLETED`);
    console.log(`🔧 Version: 4.5.0 - Fixed for Android compatibility`);
    console.log(`📋 New endpoints:`);
    console.log(`   - PUT /api/parkinglogs/:id/payment (Payment processing)`);
    console.log(`   - GET /api/payment-info/:plateNumber (Combined data)`);
    console.log(`   - POST /api/parkinglogs (Create new log)`);
    console.log(`   - POST /api/sync-events-to-parkinglogs (Sync data)`);
});