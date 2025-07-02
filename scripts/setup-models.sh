#!/bin/bash

echo "Setting up models directory..."

# Create models directory if it doesn't exist
mkdir -p models

echo "Models directory structure:"
echo "models/"
echo "├── tabnet_best.onnx          # Your ONNX model file"
echo "├── scaler.pkl                # Your StandardScaler"
echo "├── label_encoder.pkl         # Your LabelEncoder"
echo "├── best_tabnet_params.json   # Your TabNet parameters"
echo "└── README.md                 # Model documentation"

# Create model documentation
cat > models/README.md << EOF
# Network Intrusion Detection Models

This directory contains the trained machine learning artifacts for network intrusion detection.

## Files:
- **tabnet_best.onnx**: TabNet model exported to ONNX format for inference
- **scaler.pkl**: StandardScaler fitted on training data 
- **label_encoder.pkl**: LabelEncoder for attack type labels
- **best_tabnet_params.json**: Optimal hyperparameters from Optuna tuning

## Model Info:
- **Dataset**: CICIDS2017
- **Features**: 52 network flow features
- **Classes**: 7 (Bots, Brute Force, DDoS, DoS, Normal Traffic, Port Scanning, Web Attacks)
- **Algorithm**: TabNet (attention-based neural network)

## Usage:
The Flink job loads these artifacts at startup and uses them for real-time inference on network flows.
EOF

echo "Please copy your model artifacts to the models/ directory:"
echo "  - tabnet_best.onnx"
echo "  - scaler.pkl" 
echo "  - label_encoder.pkl"
echo "  - best_tabnet_params.json"