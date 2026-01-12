import React, { useRef, useEffect } from 'react';

interface EmailViewerProps {
  html: string;
}

/**
 * Renders HTML email content in an isolated iframe to prevent
 * email styles from leaking into the parent document.
 */
export const EmailViewer: React.FC<EmailViewerProps> = ({ html }) => {
  const iframeRef = useRef<HTMLIFrameElement>(null);

  useEffect(() => {
    if (iframeRef.current) {
      const iframe = iframeRef.current;
      const doc = iframe.contentDocument || iframe.contentWindow?.document;
      if (doc) {
        doc.open();
        doc.write(html);
        doc.close();
      }
    }
  }, [html]);

  return (
    <iframe
      ref={iframeRef}
      title="Email content"
      style={{
        width: '100%',
        height: '100%',
        border: 'none',
        display: 'block',
      }}
    />
  );
};
