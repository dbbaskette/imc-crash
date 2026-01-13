import React, { useRef, useEffect, useState } from 'react';

interface EmailViewerProps {
  html: string;
}

/**
 * Renders HTML email content in an isolated iframe to prevent
 * email styles from leaking into the parent document.
 */
export const EmailViewer: React.FC<EmailViewerProps> = ({ html }) => {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [height, setHeight] = useState(600);

  useEffect(() => {
    if (iframeRef.current) {
      const iframe = iframeRef.current;
      const doc = iframe.contentDocument || iframe.contentWindow?.document;
      if (doc) {
        doc.open();
        doc.write(html);
        doc.close();

        // Auto-resize iframe to content height
        const resizeObserver = new ResizeObserver(() => {
          if (doc.body) {
            const newHeight = Math.max(doc.body.scrollHeight, 400);
            setHeight(newHeight);
          }
        });

        if (doc.body) {
          resizeObserver.observe(doc.body);
          // Initial height
          setTimeout(() => {
            if (doc.body) {
              setHeight(Math.max(doc.body.scrollHeight, 400));
            }
          }, 100);
        }

        return () => resizeObserver.disconnect();
      }
    }
  }, [html]);

  return (
    <iframe
      ref={iframeRef}
      title="Email content"
      style={{
        width: '100%',
        height: `${height}px`,
        border: 'none',
        display: 'block',
      }}
    />
  );
};
