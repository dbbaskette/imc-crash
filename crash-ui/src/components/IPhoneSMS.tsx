import React from 'react';

interface IPhoneSMSProps {
  message: string;
  timestamp: string;
  senderName?: string;
}

/**
 * Renders an SMS message in an iPhone mockup styled like iMessage.
 */
export const IPhoneSMS: React.FC<IPhoneSMSProps> = ({
  message,
  timestamp,
  senderName = 'Insurance MegaCorp'
}) => {
  const time = new Date(timestamp).toLocaleTimeString([], {
    hour: 'numeric',
    minute: '2-digit'
  });

  return (
    <div className="flex justify-center p-6">
      {/* iPhone Frame */}
      <div className="relative w-[320px] bg-black rounded-[3rem] p-3 shadow-2xl">
        {/* Dynamic Island */}
        <div className="absolute top-4 left-1/2 -translate-x-1/2 w-28 h-8 bg-black rounded-full z-20" />

        {/* Screen */}
        <div className="bg-gray-100 rounded-[2.4rem] overflow-hidden">
          {/* Status Bar */}
          <div className="bg-gray-100 px-8 pt-4 pb-2 flex justify-between items-center text-xs font-semibold text-black">
            <span>{time}</span>
            <div className="flex items-center gap-1">
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="currentColor">
                <path d="M2 17h2v4H2v-4zm4-5h2v9H6v-9zm4-4h2v13h-2V8zm4-4h2v17h-2V4zm4 7h2v10h-2V11z"/>
              </svg>
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="currentColor">
                <path d="M1 9l2 2c4.97-4.97 13.03-4.97 18 0l2-2C16.93 2.93 7.08 2.93 1 9zm8 8l3 3 3-3c-1.65-1.66-4.34-1.66-6 0zm-4-4l2 2c2.76-2.76 7.24-2.76 10 0l2-2C15.14 9.14 8.87 9.14 5 13z"/>
              </svg>
              <div className="flex items-center">
                <div className="w-6 h-3 border border-black rounded-sm flex items-center p-0.5">
                  <div className="w-full h-full bg-green-500 rounded-xs" />
                </div>
              </div>
            </div>
          </div>

          {/* Messages Header */}
          <div className="bg-gray-100 border-b border-gray-200">
            <div className="flex items-center justify-between px-4 py-2">
              <button className="text-blue-500 text-sm font-medium flex items-center gap-1">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                </svg>
                <span>Messages</span>
              </button>
              <div className="flex flex-col items-center">
                <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center text-white font-bold text-sm">
                  IM
                </div>
                <span className="text-xs font-semibold text-black mt-1">{senderName}</span>
              </div>
              <div className="w-16" /> {/* Spacer for centering */}
            </div>
          </div>

          {/* Messages Area */}
          <div className="bg-white min-h-[400px] p-4 flex flex-col">
            {/* Timestamp */}
            <div className="text-center text-xs text-gray-500 mb-4">
              Today {time}
            </div>

            {/* Message Bubble */}
            <div className="flex justify-start mb-2">
              <div className="max-w-[85%] bg-gray-200 rounded-2xl rounded-tl-md px-4 py-2 text-sm text-black whitespace-pre-wrap">
                {message}
              </div>
            </div>

            {/* Delivered indicator */}
            <div className="text-right text-xs text-gray-400 mt-1">
              Delivered
            </div>

            {/* Spacer */}
            <div className="flex-1" />

            {/* Input Area */}
            <div className="flex items-center gap-2 pt-4 border-t border-gray-100">
              <button className="w-8 h-8 rounded-full bg-gray-200 flex items-center justify-center text-gray-500">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                </svg>
              </button>
              <div className="flex-1 bg-gray-100 rounded-full px-4 py-2 text-sm text-gray-400">
                iMessage
              </div>
              <button className="w-8 h-8 rounded-full bg-blue-500 flex items-center justify-center text-white">
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 10l7-7m0 0l7 7m-7-7v18" />
                </svg>
              </button>
            </div>
          </div>

          {/* Home Indicator */}
          <div className="bg-white pb-2 pt-1 flex justify-center">
            <div className="w-32 h-1 bg-black rounded-full" />
          </div>
        </div>
      </div>
    </div>
  );
};
