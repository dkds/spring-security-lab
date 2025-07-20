import { useState } from "react";
import "./App.css";

function Login() {
  const [error] = useState(false);

  return (
    <div style={styles.container}>
      <h2>Sign In</h2>

      <form method="POST" action="/login" style={styles.form}>
        <div style={styles.inputGroup}>
          <label htmlFor="username">Username: </label>
          <input type="text" name="username" id="username" required />
        </div>

        <div style={styles.inputGroup}>
          <label htmlFor="password">Password: </label>
          <input type="password" name="password" id="password" required />
        </div>

        <button type="submit" style={styles.button}>
          Login
        </button>

        {error && (
          <p style={{ color: "red" }}>Invalid credentials. Try again.</p>
        )}
      </form>
    </div>
  );
}

const styles = {
  container: {
    maxWidth: "400px",
    margin: "100px auto",
    padding: "20px",
    border: "1px solid #ddd",
    borderRadius: "8px",
    fontFamily: "Arial, sans-serif",
  },
  form: {
    display: "flex",
    flexDirection: "column",
  },
  inputGroup: {
    marginBottom: "15px",
  },
  button: {
    padding: "10px",
    backgroundColor: "#0052cc",
    color: "#fff",
    border: "none",
    borderRadius: "4px",
  },
};

export default Login;
