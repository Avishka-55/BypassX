import nodemailer from 'nodemailer'

const transporter = nodemailer.createTransport({
    host:"smtp-relay.brevo.com",
    secure: false,
    port: 587,
    auth:{
        user: process.env.SMTP_USER,
        pass: process.env.SMTP_PASS
    }
})

transporter.verify((err, success) => {
  console.log("SMTP VERIFY:", err || success);
});

export default transporter

