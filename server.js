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
mongoose.connect(mongoURI, { useNewUrlParser: true, useUnifiedTopology: true })
    .then(() => console.log('✅ Kết nối MongoDB thành công'))
    .catch(err => console.error('❌ Lỗi kết nối MongoDB:', err));

// ✅ Schema cho vehicles (cập nhật theo diagram)
const vehicleSchema = new mongoose.Schema({
    plateNumber: { type: String, required: true, unique: true },
    vehicleType: {
        type: String,
        enum: ['CAR_UNDER_9', 'CAR_9_TO_16', 'MOTORCYCLE', 'TRUCK', 'BUS'],
        default: 'CAR_UNDER_9'
    },
    createdAt: { type: Date, default: Date.now }
}, {
    collection: 'vehicles'
});

// ✅ Schema cho parking_logs (cập nhật để phù hợp với diagram)
const parkingLogSchema = new mongoose.Schema({
    vehicle_id: { type: mongoose.Schema.Types.ObjectId, ref: 'Vehicle', required: true },
    slot_id: { type: mongoose.Schema.Types.ObjectId, ref: 'ParkingSlot' },
    staff_id: { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
    timeIn: { type: Date, required: true },
    timeOut: { type: Date, default: null },
    status: {
        type: String,
        enum: ['IN_PROGRESS', 'COMPLETED', 'PAID'],
        default: 'IN_PROGRESS'
    },
    note: String,
    fee: { type: Number, default: 0 },
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
    paymentDate: { type: Date, default: null },
    // Thêm event references
    entry_event_id: { type: mongoose.Schema.Types.ObjectId, ref: 'Event' },
    exit_event_id: { type: mongoose.Schema.Types.ObjectId, ref: 'Event' }
}, {
    timestamps: true,
    collection: 'parking_logs'
});

// ✅ Schema cho parking_slots (theo diagram)
const parkingSlotSchema = new mongoose.Schema({
    slotCode: { type: String, required: true, unique: true },
    isAvailable: { type: Boolean, default: true },
    vehicleType: {
        type: String,
        enum: ['CAR_UNDER_9', 'CAR_9_TO_16', 'MOTORCYCLE', 'TRUCK', 'BUS'],
        default: 'CAR_UNDER_9'
    },
    note: String
}, {
    collection: 'parking_slots'
});

// ✅ Schema cho users (theo diagram)
const userSchema = new mongoose.Schema({
    username: { type: String, required: true, unique: true },
    password: { type: String, required: true },
    fullName: String,
    email: String,
    phone: String,
    role: {
        type: String,
        enum: ['ADMIN', 'STAFF'],
        default: 'STAFF'
    },
    status: {
        type: String,
        enum: ['ACTIVE', 'INACTIVE', 'BANNED'],
        default: 'ACTIVE'
    },
    createdAt: { type: Date, default: Date.now }
}, {
    collection: 'users'
});

// ✅ Schema cho transactions (theo diagram)
const transactionSchema = new mongoose.Schema({
    log_id: { type: mongoose.Schema.Types.ObjectId, ref: 'ParkingLog', required: true },
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
    timestamps: true,
    collection: 'transactions'
});

// ✅ Schema cho events (theo diagram)
const eventSchema = new mongoose.Schema({
    camera_id: { type: mongoose.Schema.Types.ObjectId, ref: 'Camera' },
    spot_id: { type: mongoose.Schema.Types.ObjectId, ref: 'ParkingSlot' },
    event_type: {
        type: String,
        enum: ['entry', 'exit'],
        required: true
    },
    plate_text: String,
    plate_confidence: Number,
    vehicle_image: String,
    plate_image: String,
    created_at: { type: Date, default: Date.now },
    vehicle_id: { type: mongoose.Schema.Types.ObjectId, ref: 'Vehicle' }
}, {
    collection: 'events'
});

// Models
const ParkingLog = mongoose.model('ParkingLog', parkingLogSchema);
const Vehicle = mongoose.model('Vehicle', vehicleSchema);
const ParkingSlot = mongoose.model('ParkingSlot', parkingSlotSchema);
const User = mongoose.model('User', userSchema);
const Transaction = mongoose.model('Transaction', transactionSchema);
const Event = mongoose.model('Event', eventSchema);

// ====== HELPER FUNCTIONS ======

// ✅ Hàm tính giá vé parking (theo pricing_rules từ diagram)
function calculateParkingFee(timeIn, timeOut, vehicleType = 'CAR_UNDER_9') {
    try {
        const diffInMs = timeOut - timeIn;
        const diffInMinutes = Math.floor(diffInMs / (1000 * 60));

        // Giá cơ bản dựa trên loại xe
        let basePrice = 3000; // Mặc định cho xe dưới 9 chỗ
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

        let fee = basePrice; // 30 phút đầu

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

// ✅ Hàm map status để tương thích với Android
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
                .populate('vehicle_id', 'plateNumber vehicleType')
                .sort({ timeOut: -1 });

            const vehicles = needPaymentLogs.map(log => ({
                _id: log._id,
                bienSoXe: log.vehicle_id ? log.vehicle_id.plateNumber : 'N/A',
                thoiGianVao: log.timeIn.toISOString(),
                thoiGianRa: log.timeOut ? log.timeOut.toISOString() : new Date().toISOString(),
                giaVe: log.fee || calculateParkingFee(log.timeIn, log.timeOut || new Date(),
                    log.vehicle_id ? log.vehicle_id.vehicleType : 'CAR_UNDER_9'),
                trangThaiThanhToan: 'chua_thanh_toan',
                vehicle: log.vehicle_id ? log.vehicle_id._id : null
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
        version: '2.2.0',
        database_structure: 'Updated to match diagram',
        endpoints: [
            'GET /api/test',
            'GET /api/activities',
            'GET /api/vehicles/need-payment',
            'GET /api/activities/license/:plateNumber',
            'PUT /api/activities/:id',
            'POST /api/print'
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

// ✅ API: Lấy tất cả activities với populate vehicle
app.get('/api/activities', async (req, res) => {
    try {
        console.log('📋 Lấy tất cả parking logs');
        const logs = await ParkingLog.find({})
            .populate('vehicle_id', 'plateNumber vehicleType')
            .populate('slot_id', 'slotCode')
            .populate('staff_id', 'username fullName')
            .sort({ timeIn: -1 });

        const activities = logs.map(log => ({
            _id: log._id,
            bienSoXe: log.vehicle_id ? log.vehicle_id.plateNumber : 'N/A',
            thoiGianVao: log.timeIn.toISOString(),
            thoiGianRa: log.timeOut ? log.timeOut.toISOString() : null,
            giaVe: log.fee || 0,
            trangThai: mapStatusToAndroid(log.status),
            trangThaiThanhToan: mapPaymentStatusToAndroid(log.paymentStatus),
            hinhThucThanhToan: mapPaymentMethodToAndroid(log.paymentMethod),
            thoiGianThanhToan: log.paymentDate ? log.paymentDate.toISOString() : null,
            daThanhToan: log.paymentStatus === 'PAID',
            ghiChu: log.note || '',
            viTriDo: log.slot_id ? log.slot_id.slotCode : 'N/A',
            nhanVien: log.staff_id ? (log.staff_id.fullName || log.staff_id.username) : 'N/A',
            vehicle: log.vehicle_id ? log.vehicle_id._id : null,
            vehicleType: log.vehicle_id ? log.vehicle_id.vehicleType : 'CAR_UNDER_9'
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
            .populate('vehicle_id', 'plateNumber vehicleType')
            .populate('slot_id', 'slotCode')
            .sort({ timeOut: -1 });

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
                giaVe = calculateParkingFee(log.timeIn, log.timeOut,
                    log.vehicle_id ? log.vehicle_id.vehicleType : 'CAR_UNDER_9');
            }

            return {
                _id: log._id,
                bienSoXe: log.vehicle_id ? log.vehicle_id.plateNumber : 'N/A',
                thoiGianVao: log.timeIn.toISOString(),
                thoiGianRa: log.timeOut ? log.timeOut.toISOString() : new Date().toISOString(),
                giaVe: giaVe || 3000,
                trangThai: 'da_ra',
                trangThaiThanhToan: 'chua_thanh_toan',
                hinhThucThanhToan: mapPaymentMethodToAndroid(log.paymentMethod),
                daThanhToan: false,
                ghiChu: log.note || '',
                viTriDo: log.slot_id ? log.slot_id.slotCode : 'N/A',
                vehicle: log.vehicle_id ? log.vehicle_id._id : null,
                vehicleType: log.vehicle_id ? log.vehicle_id.vehicleType : 'CAR_UNDER_9'
            };
        });

        // Cập nhật fee nếu chưa có
        for (let i = 0; i < needPaymentLogs.length; i++) {
            if (!needPaymentLogs[i].fee && vehicles[i].giaVe) {
                needPaymentLogs[i].fee = vehicles[i].giaVe;
                await needPaymentLogs[i].save();
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

// ✅ API: Lấy thông tin xe theo biển số
app.get('/api/activities/license/:plateNumber', async (req, res) => {
    try {
        const { plateNumber } = req.params;
        console.log('🔍 Tìm xe theo biển số:', plateNumber);

        // Tìm vehicle trước
        const vehicle = await Vehicle.findOne({ plateNumber: plateNumber });
        if (!vehicle) {
            return res.status(404).json({
                success: false,
                message: 'Không tìm thấy xe với biển số này'
            });
        }

        // Tìm parking log gần nhất chưa thanh toán
        const parkingLog = await ParkingLog.findOne({
            vehicle_id: vehicle._id,
            $or: [
                { paymentStatus: 'PENDING' },
                { paymentStatus: { $exists: false } },
                { status: { $in: ['IN_PROGRESS', 'COMPLETED'] } }
            ]
        })
            .sort({ timeIn: -1 })
            .populate('vehicle_id', 'plateNumber vehicleType')
            .populate('slot_id', 'slotCode');

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
            parkingLog.timeOut = timeOut;
            parkingLog.status = 'COMPLETED';
            await parkingLog.save();
        }

        // Tính fee nếu chưa có
        if (!parkingLog.fee && timeOut) {
            parkingLog.fee = calculateParkingFee(parkingLog.timeIn, timeOut,
                parkingLog.vehicle_id.vehicleType);
            await parkingLog.save();
        }

        const result = {
            _id: parkingLog._id,
            bienSoXe: parkingLog.vehicle_id.plateNumber,
            thoiGianVao: parkingLog.timeIn.toISOString(),
            thoiGianRa: timeOut ? timeOut.toISOString() : null,
            giaVe: parkingLog.fee || 0,
            trangThai: mapStatusToAndroid(parkingLog.status),
            trangThaiThanhToan: mapPaymentStatusToAndroid(parkingLog.paymentStatus),
            hinhThucThanhToan: mapPaymentMethodToAndroid(parkingLog.paymentMethod),
            thoiGianThanhToan: parkingLog.paymentDate ? parkingLog.paymentDate.toISOString() : null,
            daThanhToan: parkingLog.paymentStatus === 'PAID',
            viTriDo: parkingLog.slot_id ? parkingLog.slot_id.slotCode : 'N/A',
            vehicle: parkingLog.vehicle_id._id,
            vehicleType: parkingLog.vehicle_id.vehicleType
        };

        console.log('✅ Tìm thấy xe:', parkingLog.vehicle_id.plateNumber);

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

        const updatedLog = await ParkingLog.findByIdAndUpdate(
            id,
            mongoUpdateData,
            { new: true }
        ).populate('vehicle_id', 'plateNumber vehicleType');

        if (!updatedLog) {
            return res.status(404).json({
                success: false,
                message: 'Không tìm thấy activity'
            });
        }

        // Tạo transaction nếu đã thanh toán
        if (mongoUpdateData.paymentStatus === 'PAID') {
            const transaction = new Transaction({
                log_id: updatedLog._id,
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
                bienSoXe: updatedLog.vehicle_id ? updatedLog.vehicle_id.plateNumber : 'N/A',
                thoiGianVao: updatedLog.timeIn.toISOString(),
                thoiGianRa: updatedLog.timeOut ? updatedLog.timeOut.toISOString() : null,
                giaVe: updatedLog.fee || 0,
                trangThai: mapStatusToAndroid(updatedLog.status),
                trangThaiThanhToan: mapPaymentStatusToAndroid(updatedLog.paymentStatus),
                hinhThucThanhToan: mapPaymentMethodToAndroid(updatedLog.paymentMethod)
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

// ✅ API: Print invoice
app.post('/api/print', async (req, res) => {
    try {
        const printData = req.body;
        console.log('🖨️ Nhận lệnh in hóa đơn:', printData);

        // Xử lý logic in hóa đơn ở đây
        // Có thể gửi đến máy in hoặc tạo file PDF

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

// ✅ Khởi động server
server.listen(PORT, () => {
    console.log(`🚀 Server đang chạy tại http://localhost:${PORT}`);
    console.log(`📡 API endpoint: http://localhost:${PORT}/api`);
    console.log(`🔌 WebSocket ready for real-time updates`);
    console.log(`📊 Database structure updated to match diagram`);
});