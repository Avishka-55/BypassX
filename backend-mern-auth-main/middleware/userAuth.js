import jwt from 'jsonwebtoken'
const userAuth = async (req, res, next) => {
  const { token: cookieToken } = req.cookies;
  const authHeader = req.headers.authorization || '';
  const bearerToken = authHeader.startsWith('Bearer ') ? authHeader.slice(7).trim() : '';
  const token = bearerToken || cookieToken;

  if (!token) return res.status(401).json({ success: false, message: "Not Authorized" });

  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    if (decoded && decoded.id) {
      req.user = { id: decoded.id }; // 👈 change here
      next();
    } else {
      return res.status(401).json({ success: false, message: "Not Authorized" });
    }
  } catch (error) {
    return res.status(401).json({ success: false, message: "Not Authorized" });
  }
};


export default userAuth