# -*- coding: utf-8 -*-
"""
Created on Mon Oct  7 21:52:04 2024

@author: bnwer
"""
import os
import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
from matplotlib.backends.backend_pdf import PdfPages


def get_data(file_path):
    data = {}
    with open(file_path, 'r') as f:
        lines = f.readlines()

        metric = None
        for line in lines:

            line = line.strip()

            if line.startswith("Metric:"):
                metric = line.split(":")[1].strip()

            elif line.startswith("Technique:"):
                line_split = line.split(":")

                technique, values_str = line_split[1].strip(), line_split[2].strip()

                if metric not in data:
                    data[metric] = {}

                if technique not in data[metric]:
                    data[metric][technique] = []

                data[metric][technique].extend([float(num.strip()) for num in values_str.split(',')])

    return data


current_path = 'PATH_TO_RESULTS'
data_dir = os.path.join(os.path.dirname(current_path), '')
output_dir = os.path.join(os.path.dirname(current_path), '')


def main():
    datafiles = os.listdir(data_dir)
    agg = 'Data'  # 'Data' or 'Median'
    suffix = '_1' # This is to add a suffix to the file names.

    #fig_size = (17.5, 3.5)
    fig_size = (13.5, 3.5)

    tech_order = ['AnyBURL',  # 'Chao1',
                  'Chao2',  # 'Coverage', 'Jackknife',
                  'Poisson', 'Binomial', 'Hypergeometric',
                  'HansenHurwitz', 'HorvitzThompsonWith']

    # Only the colors we need!
    bright_palette = sns.color_palette("pastel")[:len(tech_order)]

    dataset_order = ['BioKG', 'Codex-L', 'FB13',
                     #'FB15K',
                     'FB15K-237',  # 'Hetionet',
                     'NELL-995',
                     'SNOMED', 'Wikidata5M',
                     'WN11', #'WN18',
                     'WN18RR', 'YAGO3-10']

    file_filters = []

    for limit in [
        'Chernoff',
        'ConfidenceInterval'
    ]:
        for acc in [
            #'0.05',
            '0.1' #, '0.15'
            ]:
            for conf in [
                # '0.01',
                '0.05' #, '0.1'
                ]:
                file_filters.append(f'_{agg}_{limit}_Acc{acc}_Conf{conf}.txt')
                for select in [
                    #'Corrupt',
                    #'NonCorrupt' #, 'Minimum' #, 'Maximum',
                    'Random'
                    ]:
                    file_filters.append(f'_{agg}_{limit}_{select}_Acc{acc}_Conf{conf}.txt')

    for file_filter in file_filters:
        data_by_dataset, metrics = {}, set()
        for file in datafiles:
            if not file.endswith(file_filter):
                continue

            print(file)
            file_path = os.path.join(data_dir, file)
            name = file.replace(file_filter, '')

            data = get_data(file_path)

            # Change name to be plotted.
            name = name.replace('_', '-')

            if name == 'FB15K237':
                name = 'FB15K-237'

            # Skipping!
            if name not in dataset_order:
                continue

            data_by_dataset[name] = data
            metrics = metrics.union(data.keys())

        for m in metrics:
            boxplot_data = []

            for dataset in data_by_dataset.keys():
                # This usually only happens with partial results.
                if m not in data_by_dataset[dataset]:
                    continue

                for technique in data_by_dataset[dataset][m].keys():
                    values = data_by_dataset[dataset][m][technique]

                    t = technique.split('_')[1]

                    boxplot_data.append({'Dataset': dataset, 'Metric': m,
                                         'Technique': t, 'Values': values})

            df = pd.DataFrame(boxplot_data)
            df = df.explode('Values')

            plt.figure(figsize=fig_size)
            plt.rcParams.update({'font.size': 11})

            sns.boxenplot(data=df, x='Dataset', order=dataset_order, y='Values', hue='Technique',
                          hue_order=tech_order, palette=bright_palette, width=0.75,
                          k_depth='proportion', outlier_prop=0.0001, legend=False, linecolor='black')

            positions = range(len(data_by_dataset))
            for pos in positions:
                plt.axvline(x=pos+0.5, color='black', linestyle='-', linewidth=0.5)

            plt.grid(axis='y', linestyle='--', linewidth=0.25)

            if m == 'Support_CPU':
                plt.ylim(0, 1000)
            if m == 'PCA_CPU':
                plt.ylim(0, 1000000)

            if 'PCAError' in m:
                plt.ylim(-1.0, 1.0)
            else:
                plt.yscale('symlog')

            plt.xticks(rotation=25) # Rotates x-axis ticks by x-degrees

            if 'CPU' in m:
                plt.ylabel('Speedup')
            elif 'Error' in m:
                plt.ylabel('Conf. Error')

                # Highlight 0
                plt.axhline(y=0, color='black', linestyle='--', linewidth=1.0)
            else:
                plt.ylabel('q-error')

                # Highlight 0
                plt.axhline(y=0, color='black', linestyle='--', linewidth=1.0)

            plt.xlabel('')

            plt.tight_layout()

            pdf_path = os.path.join(output_dir, f'{m}_{file_filter}{suffix}.pdf')
            with PdfPages(pdf_path, keep_empty=False) as pdf:
                pdf.savefig()
                pdf.close()


if __name__ == "__main__":
    main()
