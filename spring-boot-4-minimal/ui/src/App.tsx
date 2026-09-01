import { BrowserRouter, Route, Routes } from "react-router";
import Callback from "./pages/Callback";
import Dashboard from "./pages/Dashboard";
import Home from "./pages/Home";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/callback" element={<Callback />} />
        <Route path="/dashboard" element={<Dashboard />} />
      </Routes>
    </BrowserRouter>
  );
}
