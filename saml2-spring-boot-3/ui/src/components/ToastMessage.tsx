import React from 'react';

type ToastMessageProps = {
  title: string;
  message: string;
};

const ToastMessage: React.FC<ToastMessageProps> = ({ title, message }) => {
  return (
    <div className="flex flex-col justify">
      <div className="font-bold">{title}</div>
      <div className="text-sm">{message}</div>
    </div>
  );
};

export default ToastMessage;
