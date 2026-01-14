import React from 'react';

interface EmailViewerProps {
  html: string;
}

/**
 * Simple HTML email renderer - just renders the content directly.
 */
export const EmailViewer: React.FC<EmailViewerProps> = ({ html }) => {
  return (
    <div
      className="p-6"
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
};
