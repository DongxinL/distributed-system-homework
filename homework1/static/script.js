document.addEventListener('DOMContentLoaded', () => {
    const testBtn = document.getElementById('testBtn');
    const resultBox = document.getElementById('result');
    const alertBtn = document.getElementById('alertBtn');

    alertBtn.addEventListener('click', () => {
        const currentTime = new Date().toLocaleString();
        alert(`Current Time: ${currentTime}`);
    });

    testBtn.addEventListener('click', async () => {
        try {
            resultBox.textContent = 'Sending request to backend...';
            const response = await fetch('/api/test');
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            
            // Format the response nicely
            const output = [
                `Backend Instance: ${data.appName}`,
                `Instance Hostname: ${data.instance}`,
                `Server Port: ${data.serverPort}`,
                `Timestamp: ${data.timestamp}`,
                `Message: ${data.message}`
            ].join('\n');
            
            resultBox.textContent = output;
        } catch (error) {
            console.error('Error:', error);
            resultBox.textContent = `Error: ${error.message}. Ensure backend is running and Nginx is configured correctly.`;
        }
    });
});
