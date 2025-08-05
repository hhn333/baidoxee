const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
const app = express();

app.use(cors());
app.use(express.json());

// Kết nối MongoDB local
mongoose.connect('mongodb+srv://chuong:maihuychuong@cluster0.wcohcgr.mongodb.net/parkinglotdb?retryWrites=true&w=majority&appName=Cluster0', {
    useNewUrlParser: true,
    useUnifiedTopology: true
})
    .then(() => console.log("✅ Kết nối MongoDB thành công"))
    .catch((err) => console.error("❌ Lỗi MongoDB: ", err));


// Tạo model người dùng
const User = mongoose.model('User', {
    name: String,
    email: String
});

// API GET - Lấy danh sách người dùng
app.get('/users', async (req, res) => {
    const users = await User.find();
    res.json(users);
});

// API POST - Thêm người dùng mới
app.post('/users', async (req, res) => {
    const user = new User(req.body);
    await user.save();
    res.json({ message: 'Thêm thành công!' });
});

// Khởi động server
app.listen(3000, () => {
    console.log('🚀 Server đang chạy tại http://localhost:3000');
});
 
