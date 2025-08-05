const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
const { Server } = require('socket.io');
const http = require('http');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 3000;

// Tạo HTTP server cho Socket.IO
const server = http.createServer(app);
const io = new Server(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST", "PUT", "DELETE"]
    }
});

// Middleware
app.use(cors());
app.use(express.json());

// ✅ Kết nối MongoDB Atlas
const mongoURI = 'mongodb+srv://chuong:maihuychuong@cluster0.wcohcgr.mongodb.net/parkinglotdb?retryWrites=true&w=majority&appName=Cluster0';
mongoose.connect(mongoURI)
    .then(() => console.log('✅ Kết nối MongoDB thành công'))
    .catch(err => console.error('❌ Lỗi kết nối MongoDB:', err));

// ✅ Schema cho events collection
const eventSchema = new mongoose.Schema({
    camera_id: { type: String, required: true },
    spot_id: { type: String, required: true },
    spot_name: { type: String, required: true },
    event_type: {
        type: String,
        enum: ['enter', 'exit'],
        required: true
    },
    timestamp: { type: Date, required: true },
    plate_text: { type: String, required: true },
    plate_confidence: { type: Number, default: 0 },
    vehicle_image: { type: String },
    plate_image: { type: String },
    location_name: { type: String },
    processed: { type: Boolean, default: false },
    created_at: { type: Date, default: Date.now },
    vehicle_id: { type: String }, // ID từ events collection
    vehicleType: {
        type: String,
        enum: ['CAR_UNDER_9', 'CAR_9_TO_16', 'MOTORCYCLE', 'TRUCK', 'BUS'],
        default: 'CAR_UNDER_9'
    }
}, {
    collection: 'events',
    timestamps: false,
    versionKey: false
});

// ✅ Schema cho parking_logs (cập nhật để tham chiếu đến events)
const parkingLogSchema = new mongoose.Schema({
    event_enter_id: { type: mongoose.Schema.Types.ObjectId, ref: 'Event', required: true },
    event_exit_id: { type: mongoose.Schema.Types.ObjectId, ref: 'Event', default: null },
    plate_text: { type: String, required: true }, // Lưu trực tiếp từ events
    timeIn: { type: Date, required: true },
    timeOut: { type: Date, default: null },
    status: {
        type: String,
        enum: ['IN_PROGRESS', 'COMPLETED', 'PAID'],
        default: 'IN_PROGRESS'
    },
    fee: { type: Number, default: 0 },
    vehicleType: {
        type: String,
        enum: ['CAR_UNDER_9', 'CAR_9_TO_16', 'MOTORCYCLE', 'TRUCK', 'BUS'],
        default: 'CAR_UNDER_9'
    },
    // Thêm các trường thanh toán
    paymentMethod: {
        type: String,
        enum: ['CASH', 'BANK_TRANSFER'],
        default: 'CASH'
    },
    paymentStatus: {
        type: String,
        enum: ['PENDING', 'PAID', 'FAILED'],
        default: 'PENDING'
    },
    paymentDate: { type: Date, default: null }
}, {
    collection: 'parking_logs',
    timestamps: false,
    versionKey: false
});

// ✅ Schema cho transactions
const transactionSchema = new mongoose.Schema({
    log: { type: mongoose.Schema.Types.ObjectId, ref: 'ParkingLog', required: true },
    amount: { type: Number, required: true },
    method: {
        type: String,
        enum: ['CASH', 'BANK_TRANSFER'],
        default: 'CASH'
    },
    status: {
        type: String,
        enum: ['PAID', 'PENDING', 'FAILED'],
        default: 'PENDING'
    },
    paidAt: { type: Date, default: Date.now }
}, {
    collection: 'transactions',
    timestamps: false,
    versionKey: false
});

// Models
const Event = mongoose.model('Event', eventSchema);
const ParkingLog = mongoose.model('ParkingLog', parkingLogSchema);
const Transaction = mongoose.model('Transaction', transactionSchema);

// ====== HELPER FUNCTIONS ======

// ✅ Helper function để xử lý Date an toàn
function safeFormatDate(dateValue) {
    if (!dateValue) return null;

    try {
        if (dateValue instanceof Date) {
            return dateValue.toISOString();
        }

        const date = new Date(dateValue);
        if (isNaN(date.getTime())) {
            return null;
        }

        return date.toISOString();
    } catch (error) {
        console.error('❌ Lỗi format date:', error);
        return null;
    }
}

// ✅ Hàm tính giá vé parking
function calculateParkingFee(timeIn, timeOut, vehicleType = 'CAR_UNDER_9') {
    try {
        const diffInMs = timeOut - timeIn;
        const diffInMinutes = Math.floor(diffInMs / (1000 * 60));

        let basePrice = 3000;
        let blockPrice = 2000;

        switch (vehicleType) {
            case 'CAR_9_TO_16':
                basePrice = 5000;
                blockPrice = 3000;
                break;
            case 'MOTORCYCLE':
                basePrice = 2000;
                blockPrice = 1000;
                break;
            case 'TRUCK':
            case 'BUS':
                basePrice = 8000;
                blockPrice = 5000;
                break;
        }

        let fee = basePrice;

        if (diffInMinutes > 30) {
            const extraBlocks = Math.ceil((diffInMinutes - 30) / 30);
            fee += extraBlocks * blockPrice;
        }

        console.log(`💰 Tính giá vé: ${diffInMinutes} phút, loại xe: ${vehicleType} = ${fee}đ`);
        return fee;
    } catch (error) {
        console.error('❌ Lỗi tính giá vé:', error);
        return 3000;
    }
}

// ✅ Hàm map status
function mapStatusToAndroid(mongoStatus) {
    switch (mongoStatus) {
        case 'IN_PROGRESS': return 'dang_do';
        case 'COMPLETED': return 'da_ra';
        case 'PAID': return 'da_thanh_toan';
        default: return 'dang_do';
    }
}

function mapPaymentMethodToAndroid(method) {
    switch (method) {
        case 'CASH': return 'tien_mat';
        case 'BANK_TRANSFER': return 'chuyen_khoan';
        default: return 'tien_mat';
    }
}

function mapPaymentStatusToAndroid(status) {
    switch (status) {
        case 'PAID': return 'da_thanh_toan';
        case 'PENDING': return 'chua_thanh_toan';
        case 'FAILED': return 'that_bai';
        default: return 'chua_thanh_toan';
    }
}

// ====== WEBSOCKET HANDLING ======
let connectedClients = new Set();

io.on('connection', (socket) => {
    console.log('📱 Client connected:', socket.id);
    connectedClients.add(socket.id);

    socket.on('request-payment-list', async () => {
        try {
            const needPaymentLogs = await ParkingLog.find({
                status: 'COMPLETED',
                paymentStatus: { $in: ['PENDING', null] }
            })
                .populate('event_enter_id')
                .populate('event_exit_id')
                .sort({ timeOut: -1 })
                .lean();

            const vehicles = needPaymentLogs.map(log => ({
                _id: log._id,
                bienSoXe: log.plate_text || 'N/A',
                thoiGianVao: safeFormatDate(log.timeIn),
                thoiGianRa: safeFormatDate(log.timeOut) || safeFormatDate(new Date()),
                giaVe: log.fee || calculateParkingFee(log.timeIn, log.timeOut || new Date(), log.vehicleType),
                trangThaiThanhToan: 'chua_thanh_toan',
                vehicleType: log.vehicleType || 'CAR_UNDER_9'
            }));

            socket.emit('payment-list-updated', {
                success: true,
                data: vehicles
            });
        } catch (error) {
            console.error('Error getting payment list:', error);
            socket.emit('payment-list-error', {
                success: false,
                message: error.message
            });
        }
    });

    socket.on('disconnect', () => {
        console.log('📱 Client disconnected:', socket.id);
        connectedClients.delete(socket.id);
    });
});

// ====== API ROUTES ======

// ✅ Route mặc định
app.get('/', (req, res) => {
    res.json({
        message: '🚀 Server Parking đang chạy...',
        version: '4.0.0',
        database_structure: 'Using events collection for plate_text',
        endpoints: [
            'GET /api/test',
            'GET /api/activities',
            'GET /api/vehicles/need-payment',
            'GET /api/activities/license/:plateNumber',
            'PUT /api/activities/:id',
            'GET /api/transactions',
            'POST /api/print',
            'GET /api/events',
            'POST /api/events/process'
        ]
    });
});

// ✅ API: Test endpoint
app.get('/api/test', (req, res) => {
    res.json({
        success: true,
        message: 'Server hoạt động bình thường',
        timestamp: new Date().toISOString(),
        database: mongoose.connection.readyState === 1 ? 'Connected' : 'Disconnected'
    });
});

// ✅ API: Lấy tất cả activities từ parking_logs
app.get('/api/activities', async (req, res) => {
    try {
        console.log('📋 Lấy tất cả parking logs');
        const logs = await ParkingLog.find({})
            .populate('event_enter_id')
            .populate('event_exit_id')
            .sort({ timeIn: -1 })
            .lean();

        const activities = logs.map(log => ({
            _id: log._id,
            bienSoXe: log.plate_text || 'N/A',
            thoiGianVao: safeFormatDate(log.timeIn),
            thoiGianRa: safeFormatDate(log.timeOut),
            giaVe: log.fee || 0,
            trangThai: mapStatusToAndroid(log.status),
            trangThaiThanhToan: mapPaymentStatusToAndroid(log.paymentStatus || 'PENDING'),
            hinhThucThanhToan: mapPaymentMethodToAndroid(log.paymentMethod || 'CASH'),
            thoiGianThanhToan: safeFormatDate(log.paymentDate),
            daThanhToan: log.paymentStatus === 'PAID',
            vehicleType: log.vehicleType || 'CAR_UNDER_9',
            event_enter_id: log.event_enter_id ? log.event_enter_id._id : null,
            event_exit_id: log.event_exit_id ? log.event_exit_id._id : null
        }));

        res.json({
            success: true,
            data: activities,
            message: `Tìm thấy ${activities.length} hoạt động`,
            timestamp: new Date().toISOString()
        });
    } catch (err) {
        console.error('❌ Lỗi khi lấy activities:', err);
        res.status(500).json({
            success: false,
            message: 'Lỗi server khi lấy activities: ' + err.message
        });
    }
});

// ✅ API: Lấy xe cần thanh toán
app.get('/api/vehicles/need-payment', async (req, res) => {
    try {
        console.log('🔍 Tìm xe cần thanh toán');

        const needPaymentLogs = await ParkingLog.find({
            status: 'COMPLETED',
            paymentStatus: { $in: ['PENDING', null] }
        })
            .populate('event_enter_id')
            .populate('event_exit_id')
            .sort({ timeOut: -1 })
            .lean();

        if (needPaymentLogs.length === 0) {
            return res.json({
                success: true,
                data: [],
                message: 'Không có xe nào cần thanh toán',
                timestamp: new Date().toISOString()
            });
        }

        const vehicles = needPaymentLogs.map(log => {
            let giaVe = log.fee;
            if (!giaVe && log.timeOut) {
                giaVe = calculateParkingFee(log.timeIn, log.timeOut, log.vehicleType);
            }

            return {
                _id: log._id,
                bienSoXe: log.plate_text || 'N/A',
                thoiGianVao: safeFormatDate(log.timeIn),
                thoiGianRa: safeFormatDate(log.timeOut) || safeFormatDate(new Date()),
                giaVe: giaVe || 3000,
                trangThai: 'da_ra',
                trangThaiThanhToan: 'chua_thanh_toan',
                hinhThucThanhToan: mapPaymentMethodToAndroid(log.paymentMethod || 'CASH'),
                daThanhToan: false,
                vehicleType: log.vehicleType || 'CAR_UNDER_9',
                event_enter_id: log.event_enter_id ? log.event_enter_id._id : null,
                event_exit_id: log.event_exit_id ? log.event_exit_id._id : null
            };
        });

        // Cập nhật fee nếu chưa có
        for (let i = 0; i < needPaymentLogs.length; i++) {
            if (!needPaymentLogs[i].fee && vehicles[i].giaVe) {
                await ParkingLog.findByIdAndUpdate(needPaymentLogs[i]._id, {
                    fee: vehicles[i].giaVe
                });
            }
        }

        console.log(`✅ Tìm thấy ${vehicles.length} xe cần thanh toán`);

        res.json({
            success: true,
            data: vehicles,
            message: `Tìm thấy ${vehicles.length} xe cần thanh toán`,
            timestamp: new Date().toISOString()
        });

    } catch (err) {
        console.error('❌ Lỗi khi lấy xe cần thanh toán:', err);
        res.status(500).json({
            success: false,
            message: 'Lỗi server khi lấy xe cần thanh toán: ' + err.message
        });
    }
});

// ✅ API: Lấy thông tin xe theo biển số từ events
app.get('/api/activities/license/:plateNumber', async (req, res) => {
    try {
        const { plateNumber } = req.params;
        console.log('🔍 Tìm xe theo biển số từ events:', plateNumber);

        // Tìm parking log theo plate_text
        const parkingLog = await ParkingLog.findOne({
            plate_text: plateNumber,
            $or: [
                { paymentStatus: 'PENDING' },
                { paymentStatus: { $exists: false } },
                { status: { $in: ['IN_PROGRESS', 'COMPLETED'] } }
            ]
        })
            .sort({ timeIn: -1 })
            .populate('event_enter_id')
            .populate('event_exit_id')
            .lean();

        if (!parkingLog) {
            return res.status(404).json({
                success: false,
                message: 'Không tìm thấy thông tin đậu xe hoặc xe đã thanh toán'
            });
        }

        // Cập nhật timeOut và status nếu cần
        let timeOut = parkingLog.timeOut;
        if (!timeOut && parkingLog.status === 'IN_PROGRESS') {
            timeOut = new Date();
            await ParkingLog.findByIdAndUpdate(parkingLog._id, {
                timeOut: timeOut,
                status: 'COMPLETED'
            });
        }

        // Tính fee nếu chưa có
        let calculatedFee = parkingLog.fee;
        if (!calculatedFee && timeOut) {
            calculatedFee = calculateParkingFee(parkingLog.timeIn, timeOut, parkingLog.vehicleType);
            await ParkingLog.findByIdAndUpdate(parkingLog._id, {
                fee: calculatedFee
            });
        }

        const result = {
            _id: parkingLog._id,
            bienSoXe: parkingLog.plate_text,
            thoiGianVao: safeFormatDate(parkingLog.timeIn),
            thoiGianRa: safeFormatDate(timeOut),
            giaVe: calculatedFee || parkingLog.fee || 0,
            trangThai: mapStatusToAndroid(parkingLog.status === 'IN_PROGRESS' && timeOut ? 'COMPLETED' : parkingLog.status),
            trangThaiThanhToan: mapPaymentStatusToAndroid(parkingLog.paymentStatus),
            hinhThucThanhToan: mapPaymentMethodToAndroid(parkingLog.paymentMethod),
            thoiGianThanhToan: safeFormatDate(parkingLog.paymentDate),
            daThanhToan: parkingLog.paymentStatus === 'PAID',
            vehicleType: parkingLog.vehicleType || 'CAR_UNDER_9',
            event_enter_id: parkingLog.event_enter_id ? parkingLog.event_enter_id._id : null,
            event_exit_id: parkingLog.event_exit_id ? parkingLog.event_exit_id._id : null
        };

        console.log('✅ Tìm thấy xe:', parkingLog.plate_text);

        res.json({
            success: true,
            data: result,
            message: 'Tìm thấy thông tin xe'
        });

    } catch (err) {
        console.error('❌ Lỗi khi lấy thông tin xe:', err);
        res.status(500).json({
            success: false,
            message: 'Lỗi server khi lấy thông tin xe: ' + err.message
        });
    }
});

// ✅ API: Cập nhật activity (parking log)
app.put('/api/activities/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const updateData = req.body;

        console.log('🔄 Cập nhật activity:', id, updateData);

        // Map Android fields to MongoDB fields
        const mongoUpdateData = {};

        if (updateData.thoiGianRa) mongoUpdateData.timeOut = new Date(updateData.thoiGianRa);
        if (updateData.giaVe) mongoUpdateData.fee = updateData.giaVe;
        if (updateData.trangThai === 'da_ra') mongoUpdateData.status = 'COMPLETED';
        if (updateData.trangThai === 'da_thanh_toan') {
            mongoUpdateData.status = 'PAID';
            mongoUpdateData.paymentStatus = 'PAID';
        }

        if (updateData.hinhThucThanhToan) {
            mongoUpdateData.paymentMethod = updateData.hinhThucThanhToan === 'tien_mat' ? 'CASH' : 'BANK_TRANSFER';
        }

        if (updateData.trangThaiThanhToan === 'da_thanh_toan') {
            mongoUpdateData.paymentStatus = 'PAID';
            mongoUpdateData.paymentDate = new Date();
        }

        if (updateData.vehicleType) {
            mongoUpdateData.vehicleType = updateData.vehicleType;
        }

        const updatedLog = await ParkingLog.findByIdAndUpdate(
            id,
            mongoUpdateData,
            { new: true }
        )
            .populate('event_enter_id')
            .populate('event_exit_id')
            .lean();

        if (!updatedLog) {
            return res.status(404).json({
                success: false,
                message: 'Không tìm thấy activity'
            });
        }

        // Tạo transaction nếu đã thanh toán
        if (mongoUpdateData.paymentStatus === 'PAID') {
            const transaction = new Transaction({
                log: updatedLog._id,
                amount: updatedLog.fee,
                method: updatedLog.paymentMethod,
                status: 'PAID'
            });
            await transaction.save();
        }

        console.log('✅ Cập nhật activity thành công');

        res.json({
            success: true,
            data: {
                _id: updatedLog._id,
                bienSoXe: updatedLog.plate_text || 'N/A',
                thoiGianVao: safeFormatDate(updatedLog.timeIn),
                thoiGianRa: safeFormatDate(updatedLog.timeOut),
                giaVe: updatedLog.fee || 0,
                trangThai: mapStatusToAndroid(updatedLog.status),
                trangThaiThanhToan: mapPaymentStatusToAndroid(updatedLog.paymentStatus),
                hinhThucThanhToan: mapPaymentMethodToAndroid(updatedLog.paymentMethod),
                thoiGianThanhToan: safeFormatDate(updatedLog.paymentDate),
                daThanhToan: updatedLog.paymentStatus === 'PAID',
                vehicleType: updatedLog.vehicleType || 'CAR_UNDER_9',
                event_enter_id: updatedLog.event_enter_id ? updatedLog.event_enter_id._id : null,
                event_exit_id: updatedLog.event_exit_id ? updatedLog.event_exit_id._id : null
            },
            message: 'Cập nhật thành công'
        });

    } catch (err) {
        console.error('❌ Lỗi khi cập nhật activity:', err);
        res.status(500).json({
            success: false,
            message: 'Lỗi server: ' + err.message
        });
    }
});

// ✅ API: Lấy tất cả transactions
app.get('/api/transactions', async (req, res) => {
    try {
        console.log('💰 Lấy tất cả transactions');

        const transactions = await Transaction.find({})
            .populate({
                path: 'log',
                populate: [
                    { path: 'event_enter_id' },
                    { path: 'event_exit_id' }
                ]
            })
            .sort({ paidAt: -1 })
            .lean();

        const formattedTransactions = transactions.map(transaction => ({
            _id: transaction._id,
            amount: transaction.amount,
            method: mapPaymentMethodToAndroid(transaction.method),
            status: mapPaymentStatusToAndroid(transaction.status),
            paidAt: safeFormatDate(transaction.paidAt),
            log: transaction.log ? {
                _id: transaction.log._id,
                bienSoXe: transaction.log.plate_text || 'N/A',
                vehicleType: transaction.log.vehicleType || 'CAR_UNDER_9',
                thoiGianVao: safeFormatDate(transaction.log.timeIn),
                thoiGianRa: safeFormatDate(transaction.log.timeOut),
                trangThai: mapStatusToAndroid(transaction.log.status),
                fee: transaction.log.fee
            } : null
        }));

        res.json({
            success: true,
            data: formattedTransactions,
            message: `Tìm thấy ${formattedTransactions.length} giao dịch`,
            timestamp: new Date().toISOString()
        });

    } catch (err) {
        console.error('❌ Lỗi khi lấy transactions:', err);
        res.status(500).json({
            success: false,
            message: 'Lỗi server khi lấy transactions: ' + err.message
        });
    }
});

// ✅ API: Print invoice
app.post('/api/print', async (req, res) => {
    try {
        const printData = req.body;
        console.log('🖨️ Nhận lệnh in hóa đơn:', printData);

        res.json({
            success: true,
            message: 'Lệnh in hóa đơn đã được xử lý',
            data: {
                printId: Date.now(),
                timestamp: new Date().toISOString()
            }
        });

    } catch (err) {
        console.error('❌ Lỗi khi xử lý lệnh in:', err);
        res.status(500).json({
            success: false,
            message: 'Lỗi xử lý lệnh in: ' + err.message
        });
    }
});

// ✅ API: Lấy tất cả events
app.get('/api/events', async (req, res) => {
    try {
        console.log('📋 Lấy tất cả events');

        const events = await Event.find({})
            .sort({ timestamp: -1 })
            .lean();

        res.json({
            success: true,
            data: events,
            message: `Tìm thấy ${events.length} events`,
            timestamp: new Date().toISOString()
        });
    } catch (err) {
        console.error('❌ Lỗi khi lấy events:', err);
        res.status(500).json({
            success: false,
            message: 'Lỗi server khi lấy events: ' + err.message
        });
    }
});

// ✅ API: Xử lý events để tạo parking logs
app.post('/api/events/process', async (req, res) => {
    try {
        console.log('🔄 Xử lý events để tạo parking logs');

        // Tìm tất cả events chưa được xử lý
        const unprocessedEvents = await Event.find({ processed: false })
            .sort({ timestamp: 1 })
            .lean();

        let processedCount = 0;
        let createdLogs = 0;

        for (const event of unprocessedEvents) {
            try {
                if (event.event_type === 'enter') {
                    // Tạo parking log mới cho event vào
                    const newLog = new ParkingLog({
                        event_enter_id: event._id,
                        plate_text: event.plate_text,
                        timeIn: event.timestamp,
                        status: 'IN_PROGRESS',
                        vehicleType: event.vehicleType || 'CAR_UNDER_9'
                    });

                    await newLog.save();
                    createdLogs++;
                    console.log(`✅ Tạo parking log cho xe vào: ${event.plate_text}`);

                } else if (event.event_type === 'exit') {
                    // Tìm parking log chưa hoàn thành cho xe này
                    const activeLog = await ParkingLog.findOne({
                        plate_text: event.plate_text,
                        status: 'IN_PROGRESS'
                    }).sort({ timeIn: -1 });

                    if (activeLog) {
                        // Cập nhật parking log với thông tin xe ra
                        const fee = calculateParkingFee(activeLog.timeIn, event.timestamp, activeLog.vehicleType);

                        await ParkingLog.findByIdAndUpdate(activeLog._id, {
                            event_exit_id: event._id,
                            timeOut: event.timestamp,
                            status: 'COMPLETED',
                            fee: fee
                        });

                        console.log(`✅ Cập nhật parking log cho xe ra: ${event.plate_text}, fee: ${fee}`);
                    } else {
                        console.log(`⚠️ Không tìm thấy parking log IN_PROGRESS cho xe: ${event.plate_text}`);
                    }
                }

                // Đánh dấu event đã được xử lý
                await Event.findByIdAndUpdate(event._id, { processed: true });
                processedCount++;

            } catch (eventError) {
                console.error(`❌ Lỗi xử lý event ${event._id}:`, eventError);
            }
        }

        res.json({
            success: true,
            data: {
                processedEvents: processedCount,
                createdLogs: createdLogs
            },
            message: `Đã xử lý ${processedCount} events, tạo ${createdLogs} parking logs mới`,
            timestamp: new Date().toISOString()
        });

    } catch (err) {
        console.error('❌ Lỗi khi xử lý events:', err);
        res.status(500).json({
            success: false,
            message: 'Lỗi server khi xử lý events: ' + err.message
        });
    }
});

// ✅ Khởi động server
server.listen(PORT, () => {
    console.log(`🚀 Server đang chạy tại http://localhost:${PORT}`);
    console.log(`📡 API endpoint: http://localhost:${PORT}/api`);
    console.log(`🔌 WebSocket ready for real-time updates`);
    console.log(`🧹 Using events collection for plate_text data`);
    console.log(`📊 Events -> ParkingLogs workflow enabled`);
});